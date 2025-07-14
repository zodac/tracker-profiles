/*
 * BSD Zero Clause License
 *
 * Copyright (c) 2024-2025 zodac.net
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

import java.util.Collection;
import java.util.List;
import net.zodac.tracker.framework.TrackerType;
import net.zodac.tracker.framework.annotation.TrackerHandler;
import net.zodac.tracker.framework.gui.DisplayUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.remote.RemoteWebDriver;

/**
 * Implementation of {@link AbstractTrackerHandler} for the {@code AvistaZ} Network of trackers.
 */
@TrackerHandler(name = "AvistaZ", type = TrackerType.CLOUDFLARE_CHECK, url = "https://avistaz.to/")
@TrackerHandler(name = "CinemaZ", type = TrackerType.CLOUDFLARE_CHECK, url = "https://cinemaz.to/")
@TrackerHandler(name = "PrivateHD", type = TrackerType.CLOUDFLARE_CHECK, url = "https://privatehd.to/")
public class AvistazNetworkTrackerHandler extends AbstractTrackerHandler {

    /**
     * Default constructor.
     *
     * @param driver      a {@link RemoteWebDriver} used to load web pages and perform UI actions
     * @param trackerUrls the URLs to the tracker
     */
    public AvistazNetworkTrackerHandler(final RemoteWebDriver driver, final Collection<String> trackerUrls) {
        super(driver, trackerUrls);
    }

    @Override
    public By loginPageSelector() {
        return By.xpath("//a[contains(text(), 'Login')]");
    }

    @Override
    protected boolean hasCloudflareCheck() {
        return true;
    }

    @Override
    protected By usernameFieldSelector() {
        return By.id("email_username");
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * For {@link AvistazNetworkTrackerHandler}, prior to clicking the login button with a successful username/password there is another field where a
     * Captcha needs to be entered. This must be done within {@link DisplayUtils#INPUT_WAIT_DURATION}.
     *
     * <p>
     * Manual user interaction:
     * <ol>
     *     <li>Enter correct captcha value</li>
     * </ol>
     */
    @Override
    protected void manualCheckBeforeLoginClick(final String trackerName) {
        LOGGER.info("\t\t >>> Waiting for user to enter captcha, for {} seconds", DisplayUtils.INPUT_WAIT_DURATION.getSeconds());

        final WebElement captchaElement = driver.findElement(By.xpath("//div[input[@name='captcha']]/div[1]"));
        scriptExecutor.highlightElement(captchaElement);
        DisplayUtils.userInputConfirmation(trackerName, "Solve the captcha");
    }

    @Override
    protected By loginButtonSelector() {
        return By.xpath("//input[@value='Login' and @type='submit']");
    }

    @Override
    protected By postLoginSelector() {
        return By.id("navbar");
    }

    @Override
    protected By profilePageSelector() {
        return By.xpath("//div[@class='ratio-bar']/div[1]/ul[1]/li[1]/a[1]");
    }

    @Override
    public Collection<By> getElementsPotentiallyContainingSensitiveInformation() {
        return List.of(
            By.xpath("//tr[td[contains(text(), 'Email')]]/td[2]"),
            By.xpath("//tr[td[contains(text(), 'IP')]]/td[2]")
        );
    }

    @Override
    public boolean hasFixedHeader() {
        final WebElement headerElement = driver.findElement(By.xpath("//nav[contains(@class, 'navbar-fixed-top')]"));
        scriptExecutor.updateCss(headerElement, "position", "static");
        return true;
    }

    @Override
    protected By logoutButtonSelector() {
        // Click the user dropdown menu bar to make the logout button interactable
        final By logoutParentSelector = By.xpath("//div[@id='navbar']/ul[contains(@class, 'navbar-right')]/li[3]");
        final WebElement logoutParent = driver.findElement(logoutParentSelector);
        clickButton(logoutParent);

        return By.xpath("//div[@id='navbar']/ul[contains(@class, 'navbar-right')]/li[3]/ul[1]/li[16]");
    }
}
