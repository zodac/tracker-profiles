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
import net.zodac.tracker.framework.TrackerHandlerType;
import net.zodac.tracker.util.ScriptExecutor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;

/**
 * Implementation of {@link AbstractTrackerHandler} for the {@code GazelleGames} tracker.
 */
@TrackerHandlerType(trackerName = "GazelleGames")
public class GazelleGamesHandler extends AbstractTrackerHandler {

    private static final double ZOOM_LEVEL_FOR_SCREENSHOT = 0.67D;
    private static final Logger LOGGER = LogManager.getLogger();

    /**
     * Default constructor.
     *
     * @param driver a {@link ChromeDriver} used to load web pages and perform UI actions
     */
    public GazelleGamesHandler(final ChromeDriver driver) {
        super(driver);
    }

    @Override
    public By loginButtonSelector() {
        return By.xpath("//input[@type='submit' and @name='login' and @value='Login' and @class='submit']");
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * For {@link GazelleGamesHandler}, prior to clicking the login button with a successful username/password there is another section. Based on this
     * pop-up, a question needs to be answered. This must be done within {@link #DEFAULT_WAIT_FOR_MANUAL_INTERACTION}.
     *
     * <p>
     * Manual user interaction:
     * <ol>
     *     <li>Select correct answer to question</li>
     * </ol>
     */
    @Override
    protected void manualCheckBeforeLoginClick() {
        LOGGER.info("\t>>> Waiting for user to select correct game title, for {} seconds", DEFAULT_WAIT_FOR_MANUAL_INTERACTION.getSeconds());
        ScriptExecutor.explicitWait(DEFAULT_WAIT_FOR_MANUAL_INTERACTION);
    }

    @Override
    public double zoomLevel() {
        return ZOOM_LEVEL_FOR_SCREENSHOT;
    }

    @Override
    protected Collection<By> getElementsPotentiallyContainingSensitiveInformation() {
        return List.of(
            By.tagName("a")
        );
    }

    @Override
    public void logout() {
        final By logoutButtonSelector = logoutButtonSelector();
        ScriptExecutor.waitForElementToAppear(driver, logoutButtonSelector, DEFAULT_WAIT_FOR_PAGE_LOAD);
        final WebElement logoutButton = driver.findElement(logoutButtonSelector);
        logoutButton.click();

        // After clicking logout, a Chrome alert appears - find and click 'Yes'
        ScriptExecutor.acceptAlert(driver);

        ScriptExecutor.waitForPageToLoad(driver, DEFAULT_WAIT_FOR_PAGE_LOAD);
        ScriptExecutor.waitForElementToAppear(driver, postLogoutElementSelector(), DEFAULT_WAIT_FOR_PAGE_LOAD);
    }

    @Override
    protected By logoutButtonSelector() {
        return By.xpath("//li[@id='nav_logout']//a[text()='[Logout]']");
    }
}
