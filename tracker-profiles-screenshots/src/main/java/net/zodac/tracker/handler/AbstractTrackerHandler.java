/*
 * BSD Zero Clause License
 *
 * Copyright (c) 2024-2026 zodac.net
 *
 * Permission to use, copy, modify, and/or distribute this software for any
 * purpose with or without fee is hereby granted.
 *
 * THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES
 * WITH REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY
 * SPECIAL, DIRECT, INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES
 * WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN
 * ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF OR
 * IN CONNECTION WITH THE USE OR PERFORMANCE OF THIS SOFTWARE.
 */

package net.zodac.tracker.handler;

import static net.zodac.tracker.framework.xpath.HtmlElement.a;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.withClass;

import java.util.List;
import java.util.Objects;
import net.zodac.tracker.app.ScreenshotOrchestrator;
import net.zodac.tracker.framework.TrackerDefinition;
import net.zodac.tracker.framework.TrackerType;
import net.zodac.tracker.framework.driver.DriverPool;
import net.zodac.tracker.framework.driver.extension.Extension;
import net.zodac.tracker.framework.exception.TrackerUnavailableException;
import net.zodac.tracker.framework.gui.DisplayUtils;
import net.zodac.tracker.framework.xpath.XpathBuilder;
import net.zodac.tracker.handler.definition.HasCloudflareCheck;
import net.zodac.tracker.handler.definition.HasProfilePageActions;
import net.zodac.tracker.handler.definition.RedactsEmail;
import net.zodac.tracker.handler.definition.RedactsIpAddress;
import net.zodac.tracker.handler.definition.RedactsIrcPasskey;
import net.zodac.tracker.handler.definition.RedactsSensitiveInformation;
import net.zodac.tracker.handler.definition.RedactsTorrentPasskey;
import net.zodac.tracker.handler.definition.TrackerTimings;
import net.zodac.tracker.handler.definition.UsesExtensions;
import net.zodac.tracker.redaction.Redactor;
import net.zodac.tracker.util.BrowserInteractionHelper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jspecify.annotations.Nullable;
import org.openqa.selenium.By;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.remote.RemoteWebDriver;

/**
 * Abstract class used to define a {@link AbstractTrackerHandler}. All implementations will be used by {@link ScreenshotOrchestrator}, if the tracker
 * is included in the tracker input file. This class lists the high-level methods required for {@link ScreenshotOrchestrator} to be able to
 * successfully generate a screenshot for a given tracker.
 *
 * <p>
 * Since each tracker website has its own UI and own page structure, each implementation of {@link AbstractTrackerHandler} will contain the
 * tracker-specific {@code Selenium} logic to perform the UI actions.
 */
public abstract class AbstractTrackerHandler implements AutoCloseable, TrackerTimings {

    /**
     * The logger instance.
     */
    protected static final Logger LOGGER = LogManager.getLogger();

    /**
     * The {@link RemoteWebDriver} instance used to load web pages and perform UI actions.
     */
    @SuppressWarnings("NullAway") // Will be set in the configure() method
    protected RemoteWebDriver driver;

    /**
     * The {@link BrowserInteractionHelper} instance to perform specific actions for each {@link AbstractTrackerHandler} implementation.
     */
    @SuppressWarnings("NullAway") // Will be set in the configure() method
    protected BrowserInteractionHelper browserInteractionHelper;

    /**
     * The {@link DisplayUtils} instance bound to this handler's {@link RemoteWebDriver}.
     */
    @SuppressWarnings("NullAway") // Will be set in the configure() method
    protected DisplayUtils displayUtils;

    /**
     * The {@link TrackerDefinition}.
     */
    @SuppressWarnings("NullAway") // Will be set in the configure() method
    protected TrackerDefinition trackerDefinition;

    /**
     * We use a no-arg constructor to instantiate the {@link AbstractTrackerHandler} to avoid needing to define a constructor for each implementation.
     * However, we still need to configure the {@link AbstractTrackerHandler} with details for the tracker, so the required fields are set here.
     *
     * <p>
     * A {@link RemoteWebDriver} is acquired from the {@link DriverPool}.
     *
     * @param trackerDefinition the {@link TrackerDefinition} for this {@link AbstractTrackerHandler}
     * @see DriverPool#acquire(TrackerType, List)
     */
    public void configure(final TrackerDefinition trackerDefinition) {
        LOGGER.trace("Configuring {}: {}", this.getClass().getSimpleName(), trackerDefinition);
        this.trackerDefinition = trackerDefinition;
        final List<Extension> extensions = this instanceof UsesExtensions trackerExtensions ? trackerExtensions.requiredExtensions() : List.of();
        driver = DriverPool.acquire(trackerDefinition.type(), extensions);
        browserInteractionHelper = new BrowserInteractionHelper(driver);
        displayUtils = DisplayUtils.withDriver(driver);
    }

    /**
     * Navigates to the home page of the tracker. Waits {@link #pageLoadDuration()} for the page to finish loading.
     */
    public void openTracker() {
        String successfulUrl = null;

        for (final String trackerUrl : trackerDefinition.urls()) {
            if (successfulUrl != null) {
                // A previous URL successfully connected, no need to try another
                break;
            }

            try {
                LOGGER.info("\t\t- '{}'", trackerUrl);
                driver.manage().timeouts().pageLoadTimeout(maximumLinkResolutionDuration());
                driver.navigate().to(trackerUrl);

                browserInteractionHelper.waitForPageToLoad(pageLoadDuration());
                successfulUrl = trackerUrl;
                LOGGER.trace("Successfully opened tracker at {}", trackerUrl);
            } catch (final WebDriverException e) {
                // If website can't be resolved, assume the site is down and attempt the next URL (if any), else rethrow exception
                if (e.getMessage() != null && e.getMessage().contains("ERR_NAME_NOT_RESOLVED")) {
                    final String errorMessage = e.getMessage().split("\n")[0];
                    LOGGER.warn("\t\t- Unable to connect: {}", errorMessage);
                } else {
                    throw e;
                }
            }
        }

        // If all possible URLs have been attempted but no connection occurred, assume the website is down
        if (successfulUrl == null) {
            throw new TrackerUnavailableException(trackerDefinition.name(), trackerDefinition.urls());
        }
    }

    /**
     * Action to be performed prior to clicking the {@link #loginButtonSelector()}, generally for trackers that require an input.
     *
     * <p>
     * Where necessary, the element to be interacted with should be highlighted in the browser.
     *
     * @see BrowserInteractionHelper#highlightElement(WebElement)
     */
    protected void preLoginNavigationAction() {
        // Do nothing by default
    }

    /**
     * For some trackers the home page does not automatically redirect to the login page. In these cases, we need to explicitly click on the login
     * link to redirect. We'll only do this navigation if {@link #loginPageSelector()} is not {@code null}.
     *
     * @param trackerName the name of the tracker
     */
    public void navigateToLoginPage(final String trackerName) {
        LOGGER.debug("\t- Navigating to login page");
        preLoginNavigationAction();
        final By loginLinkSelector = loginPageSelector();

        if (loginLinkSelector != null) {
            final WebElement loginLink = browserInteractionHelper.waitForElementToBeInteractable(loginLinkSelector, pageLoadDuration());
            clickButton(loginLink);
            if (this instanceof HasCloudflareCheck hasCloudflareCheck) {
                hasCloudflareCheck.cloudflareCheck(driver, pageLoadDuration(), trackerName);
            }
            browserInteractionHelper.waitForElementToBePresent(usernameFieldSelector(), pageLoadDuration());
        } else if (this instanceof HasCloudflareCheck hasCloudflareCheck) {
            hasCloudflareCheck.cloudflareCheck(driver, pageLoadDuration(), trackerName);
        }
    }

    /**
     * For some trackers the home page does not automatically redirect to the login page. In these cases, we define a {@link By} selector of the
     * {@link WebElement} to navigate to the login page, for trackers. Is {@code null} by default as we assume this navigation is unnecessary. Should
     * be overridden otherwise.
     *
     * @return the login page {@link By} selector
     */
    @Nullable
    protected By loginPageSelector() {
        return null;
    }

    /**
     * Enters the user's credential and logs in to the tracker. Waits {@link #pageLoadDuration()} for the page to finish loading.
     *
     * @param username    the user's username for the tracker
     * @param password    the user's password for the tracker
     * @param trackerName the name of the tracker
     */
    public void login(final String username, final String password, final String trackerName) {
        LOGGER.trace("Logging in to tracker '{}'", trackerName);
        browserInteractionHelper.waitForPageToLoad(pageLoadDuration());

        try {
            LOGGER.trace("Entering username");
            final WebElement usernameField = browserInteractionHelper.waitForElementToBeInteractable(usernameFieldSelector(), pageLoadDuration());
            usernameField.clear();
            usernameField.sendKeys(username);
        } catch (final TimeoutException e) {
            throw new TimeoutException("Unable to find username field, tracker may not have loaded", e);
        }

        LOGGER.trace("Entering password");
        final By passwordFieldSelector = passwordFieldSelector();
        final WebElement passwordField = browserInteractionHelper.waitForElementToBeInteractable(passwordFieldSelector, pageLoadDuration());
        passwordField.clear();
        passwordField.sendKeys(password);

        final String loginPageUrl = driver.getCurrentUrl();
        preLoginClickAction();
        final By loginButtonSelector = loginButtonSelector();
        if (loginButtonSelector != null) {
            final String currentUrl = driver.getCurrentUrl();
            if (Objects.equals(loginPageUrl, currentUrl)) {
                final WebElement loginButton = browserInteractionHelper.waitForElementToBeInteractable(loginButtonSelector, pageLoadDuration());
                LOGGER.trace("Clicking login button: {}", loginButton);
                clickButton(loginButton);
            } else {
                LOGGER.trace("Page has redirected from '{}' to '{}', assuming no need to click login button", loginPageUrl, currentUrl);
            }
        }
        postLoginClickAction();

        try {
            final By postLoginSelector = postLoginSelector();
            LOGGER.trace("Logged in, waiting for post-login selector: {}", postLoginSelector);
            browserInteractionHelper.waitForElementToBePresent(postLoginSelector, pageLoadDuration());
        } catch (final TimeoutException e) {
            throw new TimeoutException("Timed out waiting for post-login selector, cannot confirm login was successful", e);
        }
    }

    /**
     * Defines the {@link By} selector of the {@link WebElement} where the username is entered to log in to the tracker.
     *
     * <p>
     * By default, we assume the username field has an {@link By#id(String)} of <b>username</b>. Should be overridden otherwise.
     *
     * @return the username field {@link By} selector
     */
    protected By usernameFieldSelector() {
        return By.name("username");
    }

    /**
     * Defines the {@link By} selector of the {@link WebElement} where the password is entered to log in to the tracker.
     *
     * <p>
     * By default, we assume the password field has an {@link By#id(String)} of <b>password</b>. Should be overridden otherwise.
     *
     * @return the password field {@link By} selector
     */
    protected By passwordFieldSelector() {
        return By.name("password");
    }

    /**
     * Action to be performed prior to clicking the {@link #loginButtonSelector()}, generally for trackers that require an input.
     *
     * <p>
     * Where necessary, the element to be interacted with should be highlighted in the browser.
     *
     * @see BrowserInteractionHelper#highlightElement(WebElement)
     */
    protected void preLoginClickAction() {
        // Do nothing by default
    }

    /**
     * Defines the {@link By} selector of the {@link WebElement} of the login button.
     *
     * @return the login button {@link By} selector
     */
    protected By loginButtonSelector() {
        return By.id("login-button");
    }

    /**
     * Action to be performed after clicking the {@link #loginButtonSelector()}, generally for trackers that require an input.
     *
     * <p>
     * Where necessary, the element to be interacted with should be highlighted in the browser.
     *
     * @see BrowserInteractionHelper#highlightElement(WebElement)
     */
    protected void postLoginClickAction() {
        // Do nothing by default
    }

    /**
     * Defines the {@link By} selector of the {@link WebElement} used to confirm that the user has successfully logged in.
     *
     * <p>
     * By default, this will use {@link #profileLinkSelector()}, but if that selector requires some additional work (reloading the page, or
     * hovering/clicking another element to open a drop-down menu), then a simpler one should be used for this check.
     *
     * @return the post-login {@link By} selector
     */
    protected By postLoginSelector() {
        return profileLinkSelector();
    }

    /**
     * Once logged in, navigates to the user's profile page on the tracker. Waits {@link #pageLoadDuration()} for the page to finish
     * loading.
     */
    public void openProfilePage() {
        LOGGER.trace("Opening profile page");
        browserInteractionHelper.waitForPageToLoad(pageTransitionsDuration());

        final WebElement profileLink = browserInteractionHelper.waitForElementToBeInteractable(profileLinkSelector(), pageTransitionsDuration());
        browserInteractionHelper.removeAttribute(profileLink, "target"); // Removing 'target="_blank"', to ensure link opens in same tab
        clickButton(profileLink);

        try {
            LOGGER.debug("\t\t- Waiting to confirm user profile page has loaded successfully");
            browserInteractionHelper.waitForPageToLoad(pageLoadDuration());
            browserInteractionHelper.waitForElementToBeVisible(profilePageElementSelector(), pageLoadDuration());
            browserInteractionHelper.moveToOrigin();
            if (this instanceof HasProfilePageActions trackerWithProfilePageActions) {
                trackerWithProfilePageActions.performActionOnProfilePage();
            }
        } catch (final TimeoutException e) {
            throw new TimeoutException("Unable to find user profile content, profile page may not have loaded", e);
        }
    }

    /**
     * Defines the {@link By} selector of the {@link WebElement} that links to the user's profile page.
     *
     * @return the profile page link {@link By} selector
     */
    protected By profileLinkSelector() {
        return XpathBuilder
            .from(a, withClass("username"))
            .build();
    }

    /**
     * Defines the {@link By} selector of a {@link WebElement} used to confirm that the user's details have loaded on the profile page.
     *
     * @return the profile page content {@link By} selector
     */
    protected abstract By profilePageElementSelector();

    /**
     * Reloads the current profile page in the browser, restoring the page to its original state (clearing any DOM mutations from redaction). Waits
     * {@link #pageLoadDuration()} for the page to finish loading, then re-runs any {@link HasProfilePageActions#performActionOnProfilePage()}.
     */
    public void reloadProfilePage() {
        LOGGER.trace("Reloading profile page to restore original state");
        driver.navigate().refresh();
        browserInteractionHelper.waitForPageToLoad(pageLoadDuration());

        browserInteractionHelper.waitForElementToBeVisible(profilePageElementSelector(), pageLoadDuration());
        if (this instanceof HasProfilePageActions trackerWithProfilePageActions) {
            trackerWithProfilePageActions.performActionOnProfilePage();
        }
    }

    /**
     * Checks if the {@link AbstractTrackerHandler} has any sensitive information that requires redaction of any elements.
     *
     * @return <code>true</code> if there are elements in need of redaction
     */
    public final boolean hasSensitiveInformation() {
        // It's not sufficient to just use instanceof, since some handlers may implement a redaction that a subclass tracker needs to override
        return (this instanceof RedactsEmail email && !email.emailElements().isEmpty())
            || (this instanceof RedactsIpAddress ip && !ip.ipAddressElements().isEmpty())
            || (this instanceof RedactsIrcPasskey irc && !irc.ircPasskeyElements().isEmpty())
            || (this instanceof RedactsTorrentPasskey torrent && !torrent.torrentPasskeyElements().isEmpty())
            || (this instanceof RedactsSensitiveInformation sensitive && !sensitive.sensitiveElements().isEmpty());
    }

    /**
     * Redacts the {@link WebElement}s on the user's profile page that hold sensitive information (email addresses, IP addresses, passkeys, etc.).
     * Each redaction category is opt-in via the corresponding interfaces:
     * <ol>
     *     <li>{@link RedactsEmail}</li>
     *     <li>{@link RedactsIpAddress}</li>
     *     <li>{@link RedactsIrcPasskey}</li>
     *     <li>{@link RedactsTorrentPasskey}</li>
     *     <li>{@link RedactsSensitiveInformation}</li>
     * </ol>
     *
     * @param redactor the {@link Redactor} to redact the sensitive information
     * @return the number of {@link WebElement}s where the text has been redacted
     * @see Redactor
     */
    // TODO: Add other redaction types (country, flag, avatar, gender, BT client, BT port, age/birthday, invitedBy)
    public int redactElements(final Redactor redactor) {
        LOGGER.trace("Redacting elements");
        int redacted = 0;

        if (this instanceof RedactsEmail emailElements) {
            LOGGER.debug("\t\t- Redacting email elements");
            redacted += emailElements.redactEmailElements(driver, redactor);
        }

        if (this instanceof RedactsIpAddress ipAddressElements) {
            LOGGER.debug("\t\t- Redacting IP address elements");
            redacted += ipAddressElements.redactIpAddressElements(driver, redactor);
        }

        if (this instanceof RedactsIrcPasskey ircPasskeyElements) {
            LOGGER.debug("\t\t- Redacting IRC passkey elements");
            redacted += ircPasskeyElements.redactIrcPasskeyElements(driver, redactor);
        }

        if (this instanceof RedactsTorrentPasskey torrentPasskeyElements) {
            LOGGER.debug("\t\t- Redacting torrent passkey elements");
            redacted += torrentPasskeyElements.redactTorrentPasskeyElements(driver, redactor);
        }

        if (this instanceof RedactsSensitiveInformation sensitiveElements) {
            LOGGER.debug("\t\t- Redacting remaining sensitive elements");
            redacted += sensitiveElements.redactSensitiveElements(driver, redactor);
        }

        return redacted;
    }

    /**
     * Any action to be taken by the {@link AbstractTrackerHandler} while on the profile page, prior to taking a screenshot.
     *
     * <p>
     * By default, it hides the scrollbar without changing page dimensions, preventing layout shifts that would affect redaction positioning.
     *
     * @see BrowserInteractionHelper#hideScrollbar()
     */
    public void actionBeforeScreenshot() {
        browserInteractionHelper.hideScrollbar();
    }

    /**
     * Any action to be taken by the {@link AbstractTrackerHandler} while on the profile page, after taking a screenshot.
     *
     * <p>
     * By default, it re-enables scrolling and scrolls back to the top of the page.
     *
     * @see BrowserInteractionHelper#scrollToTheTop()
     * @see BrowserInteractionHelper#showScrollbar()
     */
    public void actionAfterScreenshot() {
        browserInteractionHelper.showScrollbar();
        browserInteractionHelper.scrollToTheTop();
    }

    /**
     * Retrieves the {@link WebElement} of the logout button.
     *
     * @return the logout button {@link WebElement}
     */
    protected abstract By logoutButtonSelector();

    /**
     * Logs out of the tracker, ending the user's session. Waits {@link #pageLoadDuration()} for the {@link #postLogoutElementSelector()} to
     * load, signifying that we have successfully logged out and been redirected to the login page.
     */
    public final void logout() {
        LOGGER.debug("\t- Logging out of tracker");
        final By logoutButtonSelector = logoutButtonSelector();
        final WebElement logoutButton = browserInteractionHelper.waitForElementToBeInteractable(logoutButtonSelector, pageTransitionsDuration());
        clickButton(logoutButton);

        additionalActionAfterLogoutClick();
        browserInteractionHelper.waitForElementToBePresent(postLogoutElementSelector(), pageLoadDuration());
    }

    /**
     * For certain trackers, additional actions may need to be performed after clicking on the logout button. For example, some trackers will have a
     * pop-up to confirm logout.
     *
     * <p>
     * This method should be overridden as required.
     */
    protected void additionalActionAfterLogoutClick() {
        // Do nothing by default
    }

    /**
     * Defines the {@link By} selectors of the {@link WebElement} that signifies that the {@link #logout()} was successfully executed.
     *
     * <p>
     * By default, we assume that we will be redirected to the login page, so this method returns {@link #usernameFieldSelector()}, or else the home
     * page, and {@link #loginPageSelector()} will be returned. Should be overridden otherwise.
     *
     * @return the post-logout button {@link WebElement}
     */
    protected By postLogoutElementSelector() {
        return loginPageSelector() == null ? usernameFieldSelector() : loginPageSelector();
    }

    @Override
    public void close() {
        LOGGER.trace("Releasing driver");
        DriverPool.release(driver);
    }

    /**
     * Retrieves the {@link RemoteWebDriver}.
     *
     * @return the {@link RemoteWebDriver}
     */
    public RemoteWebDriver driver() {
        return driver;
    }

    /**
     * Sometimes when clicking the login button or the profile page button, the page won't load correctly. If a {@link TimeoutException} occurs due to
     * the web page, not loading within {@code clickResolutionDuration} it is simply ignored. We then force the web page to stop loading before
     * proceeding.
     *
     * @param buttonToClick the {@link WebElement} to {@link WebElement#click()}
     * @see BrowserInteractionHelper#stopPageLoad()
     */
    protected void clickButton(final WebElement buttonToClick) {
        try {
            LOGGER.trace("Clicking: {}", buttonToClick);
            driver.manage().timeouts().pageLoadTimeout(maximumClickResolutionDuration());
            buttonToClick.click();
        } catch (final TimeoutException e) {
            LOGGER.debug("\t\t- Page still loading after {}, force stopping page load", maximumClickResolutionDuration());
            LOGGER.trace(e);
            browserInteractionHelper.stopPageLoad();
        } catch (final Exception e) {
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace(driver.getPageSource());
                LOGGER.trace(e);
            }
            throw e;
        }

        driver.manage().timeouts().pageLoadTimeout(maximumLinkResolutionDuration());
    }
}
