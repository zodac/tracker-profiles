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

import java.util.Collection;
import java.util.List;
import net.zodac.tracker.framework.TrackerType;
import net.zodac.tracker.framework.annotation.TrackerHandler;
import net.zodac.tracker.framework.gui.DisplayUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;

/**
 * Implementation of {@link AbstractTrackerHandler} for the {@code DigitalCore.Club} tracker.
 */
@TrackerHandler(name = "DigitalCore.Club", type = TrackerType.MANUAL, url = {
    "https://digitalcore.club/",
    "https://prxy.digitalcore.club/"
})
public class DigitalCoreClubHandler extends AbstractTrackerHandler {

    private static final Logger LOGGER = LogManager.getLogger();

    /**
     * Constructs a new {@link DigitalCoreClubHandler}.
     *
     * @param driver      a {@link ChromeDriver} used to load web pages and perform UI actions
     * @param trackerUrls the URLs to the tracker
     */
    public DigitalCoreClubHandler(final ChromeDriver driver, final Collection<String> trackerUrls) {
        super(driver, trackerUrls);
    }

    @Override
    protected By usernameFieldSelector() {
        return By.id("inputUsername");
    }

    @Override
    protected By passwordFieldSelector() {
        return By.id("inputPassword");
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * For {@link DigitalCoreClubHandler}, prior to clicking the login button with a successful username/password there is another field where a
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

        final WebElement captchaElement = driver.findElement(By.id("captcha"));
        scriptExecutor.highlightElement(captchaElement);
        DisplayUtils.userInputConfirmation(trackerName, "Solve the captcha");
    }

    @Override
    protected By loginButtonSelector() {
        return By.xpath("//button[text()='Login' and @type='submit']");
    }

    @Override
    protected By postLoginSelector() {
        return By.tagName("main-menu");
    }

    @Override
    protected By profilePageSelector() {
        return By.xpath("//user//a[1]");
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * For {@link DigitalCoreClubHandler}, the page loads but the table with user details is not visible on the initial load. So we wait for the user
     * details table to be visible before proceeding.
     */
    @Override
    protected void additionalActionOnProfilePage() {
        // Reload the page
        driver.navigate().refresh();
        scriptExecutor.waitForPageToLoad(DEFAULT_WAIT_FOR_PAGE_LOAD);

        final By selector = By.xpath("//div[@id='contentContainer']//table");
        scriptExecutor.waitForElementToAppear(selector, DEFAULT_WAIT_FOR_PAGE_LOAD);
    }

    @Override
    protected By logoutButtonSelector() {
        return By.xpath("//span[@class='hidden-xs2' and text()='Sign out']");
    }
}
