/*
 * BSD Zero Clause License
 *
 * Copyright (c) 2021-2024 zodac.me
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

package me.zodac.tracker.handler;

import me.zodac.tracker.framework.TrackerAccessibility;
import me.zodac.tracker.framework.TrackerHandlerType;
import me.zodac.tracker.util.ScriptExecutor;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;

/**
 * Implementation of {@link AbstractTrackerHandler} for the {@code AnimeBytes} tracker.
 */
@TrackerHandlerType(trackerName = "AnimeBytes", accessibility = TrackerAccessibility.PRIVATE)
public class AnimeBytesHandler extends AbstractTrackerHandler {

    private static final double ZOOM_LEVEL_FOR_SCREENSHOT = 0.9D;

    /**
     * Default constructor.
     *
     * @param driver a {@link ChromeDriver} used to load web pages and perform UI actions
     */
    public AnimeBytesHandler(final ChromeDriver driver) {
        super(driver);
    }

    @Override
    public By loginButtonSelector() {
        return By.xpath("//input[@value='Log In!' and @type='submit']");
    }

    @Override
    public double zoomLevelForScreenshot() {
        return ZOOM_LEVEL_FOR_SCREENSHOT;
    }

    @Override
    public void logout() {
        final WebElement logoutButton = driver.findElement(logoutButtonSelector());
        logoutButton.click();

        // After clicking logout, a confirmation box appears - find and click 'Yes'
        final By logoutConfirmationSelector = By.xpath("//form[@id='tokenconfirm']//input[@name='yes' and @type='submit']");
        ScriptExecutor.waitForElementToAppear(driver, logoutConfirmationSelector, DEFAULT_WAIT_FOR_TRANSITIONS);
        final WebElement logoutConfirmation = driver.findElement(logoutConfirmationSelector);
        logoutConfirmation.click();

        ScriptExecutor.waitForElementToAppear(driver, postLogoutElementSelector(), DEFAULT_WAIT_FOR_PAGE_LOAD);
    }

    @Override
    protected By logoutButtonSelector() {
        // Click the user dropdown menu bar to make the logout button interactable
        final By logoutParentBy = By.xpath("//li[@id='username_menu']//span[contains(@class, 'clickmenu')]");
        final WebElement logoutParent = driver.findElement(logoutParentBy);
        logoutParent.click();

        return By.xpath("//li[@id='username_menu']//ul[contains(@class, 'subnav')]//a[text()='Logout']");
    }

    @Override
    protected By postLogoutElementSelector() {
        return By.id("nav_login");
    }
}
