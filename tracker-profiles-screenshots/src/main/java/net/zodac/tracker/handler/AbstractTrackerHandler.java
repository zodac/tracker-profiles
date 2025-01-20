/*
 * BSD Zero Clause License
 *
 * Copyright (c) 2021-2025 zodac.net
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

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.List;
import net.zodac.tracker.ProfileScreenshotter;
import net.zodac.tracker.framework.Configuration;
import net.zodac.tracker.framework.ConfigurationProperties;
import net.zodac.tracker.framework.TrackerDefinition;
import net.zodac.tracker.util.ScreenshotTaker;
import net.zodac.tracker.util.ScriptExecutor;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;

/**
 * Abstract class used to define a {@link AbstractTrackerHandler}. All implementations will be used by {@link ProfileScreenshotter},
 * if the tracker is included in the {@code trackers.csv} input file. This class lists the high-level methods required for
 * {@link ProfileScreenshotter} to be able to successfully generate a screenshot for a given tracker.
 *
 * <p>
 * Since each tracker website has its own UI and own page structure, each implementation of {@link AbstractTrackerHandler} will contain the
 * tracker-specific {@code selenium} logic to perform the UI actions.
 */
public abstract class AbstractTrackerHandler implements AutoCloseable {

    /**
     * The {@link ConfigurationProperties} for the application.
     */
    protected static final ConfigurationProperties CONFIG = Configuration.get();

    /**
     * The default wait {@link Duration} when waiting for a web page load.
     */
    protected static final Duration DEFAULT_WAIT_FOR_PAGE_LOAD = Duration.of(5L, ChronoUnit.SECONDS);

    /**
     * The default wait {@link Duration} when waiting for an element to be clicked or a page load to begin.
     */
    protected static final Duration DEFAULT_WAIT_FOR_TRANSITIONS = Duration.of(500L, ChronoUnit.MILLIS);

    /**
     * The {@link ChromeDriver} instance used to load web pages and perform UI actions.
     */
    protected ChromeDriver driver;

    /**
     * Default constructor, only for implementation classes.
     *
     * @param driver a {@link ChromeDriver} used to load web pages and perform UI actions
     */
    protected AbstractTrackerHandler(final ChromeDriver driver) {
        this.driver = driver;
    }

    /**
     * Navigates to the login page of the tracker. Waits {@link #DEFAULT_WAIT_FOR_PAGE_LOAD} for the page to finish loading.
     *
     * @param trackerDefinition the {@link TrackerDefinition} containing the login page URL
     */
    public void openLoginPage(final TrackerDefinition trackerDefinition) {
        driver.navigate().to(trackerDefinition.loginLink());
        ScriptExecutor.waitForPageToLoad(driver, DEFAULT_WAIT_FOR_PAGE_LOAD);
    }

    /**
     * Enters the user's credential and logs in to the tracker. Waits {@link #DEFAULT_WAIT_FOR_PAGE_LOAD} for the page to finish loading.
     *
     * @param trackerDefinition the {@link TrackerDefinition} containing the login credentials
     */
    public void login(final TrackerDefinition trackerDefinition) {
        final WebElement usernameField = driver.findElement(usernameFieldSelector());
        usernameField.clear();
        usernameField.sendKeys(trackerDefinition.username());

        final WebElement passwordField = driver.findElement(passwordFieldSelector());
        passwordField.clear();
        passwordField.sendKeys(trackerDefinition.password());

        final WebElement loginButton = driver.findElement(loginButtonSelector());
        loginButton.click();

        ScriptExecutor.waitForPageToLoad(driver, DEFAULT_WAIT_FOR_PAGE_LOAD);
    }

    /**
     * Defines the {@link By} selector of the {@link WebElement} where the username is entered to log in to the tracker.
     *
     * <p>
     * By default, we assume the username field has an <i>id</i> of <b>username</b>. Should be overridden otherwise.
     *
     * @return the username field {@link By} selector
     */
    protected By usernameFieldSelector() {
        return By.id("username");
    }

    /**
     * Defines the {@link By} selector of the {@link WebElement} where the password is entered to log in to the tracker.
     *
     * <p>
     * By default, we assume the password field has an <i>id</i> of <b>password</b>. Should be overridden otherwise.
     *
     * @return the password field {@link By} selector
     */
    protected By passwordFieldSelector() {
        return By.id("password");
    }

    /**
     * Defines the {@link By} selector of the {@link WebElement} of the login button.
     *
     * @return the login button {@link By} selector
     */
    protected abstract By loginButtonSelector();

    /**
     * Checks if there is a banner on the tracker web page, and closes it. This may be a cookie banner, or some other warning banner that can
     * obscure content, or expose unwanted information.
     *
     * <p>
     * By default, we assume there is no banner to clear, so this method returns {@code false}. Should be overridden otherwise.
     *
     * @return {@code true} if there was a banner, and it was cleared
     */
    public boolean canBannerBeCleared() {
        return false;
    }

    /**
     * Once logged in, navigates to the user's profile page on the tracker. Waits {@link #DEFAULT_WAIT_FOR_PAGE_LOAD} for the page to finish
     * loading.
     *
     * @param trackerDefinition the {@link TrackerDefinition} containing the user's profile URL
     */
    public void openProfilePage(final TrackerDefinition trackerDefinition) {
        driver.navigate().to(trackerDefinition.profilePage());
        ScriptExecutor.waitForPageToLoad(driver, DEFAULT_WAIT_FOR_PAGE_LOAD);
    }

    /**
     * Defines the zoom percentage required for the tracker in order or all relevant details to be shown on the profile page and correctly screenshot.
     *
     * <p>
     * By default, we assume the default zoom level is acceptable, so this method returns {@link ScreenshotTaker#DEFAULT_ZOOM_LEVEL}. Should be
     * overridden otherwise.
     *
     * @return the zoom level required for the {@link AbstractTrackerHandler}
     * @see ScriptExecutor#zoom(JavascriptExecutor, double)
     */
    public double zoomLevel() {
        return ScreenshotTaker.DEFAULT_ZOOM_LEVEL;
    }

    /**
     * Retrieves a {@link Collection} of {@link WebElement}s from the user's profile page, where the inner text needs to be redacted. This is used for
     * {@link WebElement}s that has sensitive information (like an IP address), which should not be visible in the screenshot. Once found, the text
     * in the {@link WebElement}s is redacted.
     *
     * @return the number of {@link WebElement}s where the text has been redacted
     * @see ScriptExecutor#redactInnerTextOf(JavascriptExecutor, WebElement)
     */
    public int redactElements() {
        final Collection<WebElement> elementsToBeRedacted = getElementsPotentiallyContainingSensitiveInformation().stream()
            .flatMap(rootSelector -> driver.findElements(rootSelector).stream())
            .filter(element -> doesElementContainEmailAddress(element) || doesElementContainIpAddress(element))
            .toList();

        for (final WebElement element : elementsToBeRedacted) {
            ScriptExecutor.redactInnerTextOf(driver, element);
        }

        return elementsToBeRedacted.size();
    }

    /**
     * Returns a {@link Collection} of {@link By} selectors which define all possible HTML elements which may contain sensitive data to be redacted.
     *
     * <p>
     * By default, we assume that there are no elements to redact, so this method returns an empty {@link List}. Should be overridden
     * otherwise.
     *
     * @return the {@link By} selectors for elements that may contain sensitive information
     */
    protected Collection<By> getElementsPotentiallyContainingSensitiveInformation() {
        return List.of();
    }

    /**
     * Logs out of the tracker, ending the user's session. Waits {@link #DEFAULT_WAIT_FOR_PAGE_LOAD} for the {@link #postLogoutElementSelector()} to
     * load, signifying that we have successfully logged out and been redirected to the login page.
     */
    public void logout() {
        final By logoutButtonSelector = logoutButtonSelector();
        ScriptExecutor.waitForElementToAppear(driver, logoutButtonSelector, DEFAULT_WAIT_FOR_PAGE_LOAD);
        final WebElement logoutButton = driver.findElement(logoutButtonSelector);
        logoutButton.click();

        ScriptExecutor.waitForPageToLoad(driver, DEFAULT_WAIT_FOR_PAGE_LOAD);
        ScriptExecutor.waitForElementToAppear(driver, postLogoutElementSelector(), DEFAULT_WAIT_FOR_TRANSITIONS);
    }

    /**
     * Defines the {@link By} selectors of the {@link WebElement} that signifies that the {@link #logout()} was successfully executed.
     *
     * <p>
     * By default, we assume that we will be redirected to the login page, so this method returns {@link #loginButtonSelector()}. Should be overridden
     * otherwise.
     *
     * @return the login button {@link WebElement}
     */
    protected By postLogoutElementSelector() {
        return loginButtonSelector();
    }

    /**
     * Retrieves the {@link WebElement} of the logout button.
     *
     * @return the logout button {@link WebElement}
     */
    protected abstract By logoutButtonSelector();

    @Override
    public void close() {
        driver.quit();
    }

    /**
     * Retrieves the {@link ChromeDriver}.
     *
     * @return the {@link ChromeDriver}
     */
    public ChromeDriver driver() {
        return driver;
    }

    private static boolean doesElementContainEmailAddress(final WebElement element) {
        return doesElementContain(element, CONFIG.emailAddresses());
    }

    private static boolean doesElementContainIpAddress(final WebElement element) {
        return doesElementContain(element, CONFIG.ipAddresses());
    }

    private static boolean doesElementContain(final WebElement element, final Collection<String> stringsToFind) {
        return stringsToFind.stream()
            .anyMatch(stringToFind -> element.getText().contains(stringToFind));
    }
}
