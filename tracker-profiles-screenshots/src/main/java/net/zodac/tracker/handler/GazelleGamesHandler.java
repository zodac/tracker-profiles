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
 * Implementation of {@link AbstractTrackerHandler} for the {@code GazelleGames} tracker.
 */
@TrackerHandler(name = "GazelleGames", type = TrackerType.MANUAL, url = "https://gazellegames.net/")
public class GazelleGamesHandler extends AbstractTrackerHandler {

    private static final Logger LOGGER = LogManager.getLogger();

    /**
     * Default constructor.
     *
     * @param driver      a {@link ChromeDriver} used to load web pages and perform UI actions
     * @param trackerUrls the URLs to the tracker
     */
    public GazelleGamesHandler(final ChromeDriver driver, final Collection<String> trackerUrls) {
        super(driver, trackerUrls);
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * For {@link GazelleGamesHandler}, prior to clicking the login button with a successful username/password there is a multiple-choice question,
     * where the correct game title must be chosen that matches the picture. This must be done within {@link DisplayUtils#INPUT_WAIT_DURATION}.
     *
     * <p>
     * Manual user interaction:
     * <ol>
     *     <li>Select correct answer to question</li>
     * </ol>
     */
    @Override
    protected void manualCheckBeforeLoginClick(final String trackerName) {
        LOGGER.info("\t\t >>> Waiting for user to select correct game title, for {} seconds", DisplayUtils.INPUT_WAIT_DURATION.getSeconds());

        final WebElement selectionElement = driver.findElement(By.xpath("//div[@id='tdwrap']/form[1]/table[1]"));
        scriptExecutor.highlightElement(selectionElement);
        DisplayUtils.userInputConfirmation(trackerName, "Select the correct game");
    }

    @Override
    protected By loginButtonSelector() {
        return By.xpath("//input[@type='submit' and @name='login' and @value='Login' and @class='submit']");
    }

    @Override
    protected By postLoginSelector() {
        return By.id("userinfo");
    }

    @Override
    protected By profilePageSelector() {
        return By.xpath("//a[contains(@class, 'username')]");
    }

    @Override
    public Collection<By> getElementsPotentiallyContainingSensitiveInformation() {
        return List.of(
            By.xpath("//ul[contains(@class, 'stats')]/li[contains(text(), 'Email')]/a[1]"), // Email
            By.xpath("//div[@id='copyright']/p[2]/a[1]/span[3]") // Footer with last used IP address
        );
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * For {@link GazelleGamesHandler}, after clicking the logout button, a Javascript alert appears, which must be accepted.
     */
    @Override
    public void logout() {
        final By logoutButtonSelector = logoutButtonSelector();
        scriptExecutor.waitForElementToAppear(logoutButtonSelector, DEFAULT_WAIT_FOR_PAGE_LOAD);
        final WebElement logoutButton = driver.findElement(logoutButtonSelector);
        clickButton(logoutButton);

        // After clicking logout, a Chrome alert appears - find and click 'Yes'
        scriptExecutor.acceptAlert();

        scriptExecutor.waitForPageToLoad(DEFAULT_WAIT_FOR_PAGE_LOAD);
        scriptExecutor.waitForElementToAppear(postLogoutElementSelector(), DEFAULT_WAIT_FOR_PAGE_LOAD);
    }

    @Override
    protected By logoutButtonSelector() {
        return By.xpath("//li[@id='nav_logout']//a[text()='[Logout]']");
    }
}
