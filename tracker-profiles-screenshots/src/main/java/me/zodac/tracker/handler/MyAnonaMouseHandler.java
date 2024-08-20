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
 * Implementation of {@link AbstractTrackerHandler} for the {@code MyAnonaMouse} tracker.
 */
@TrackerHandlerType(trackerName = "MyAnonaMouse", accessibility = TrackerAccessibility.PRIVATE)
public class MyAnonaMouseHandler extends AbstractTrackerHandler {

    private static final double ZOOM_LEVEL_FOR_SCREENSHOT = 0.67D;

    /**
     * Default constructor.
     *
     * @param driver a {@link ChromeDriver} used to load web pages and perform UI actions
     */
    public MyAnonaMouseHandler(final ChromeDriver driver) {
        super(driver);
    }

    @Override
    protected By usernameFieldSelector() {
        return By.xpath("//input[@type='email' and @name='email']");
    }

    @Override
    protected By passwordFieldSelector() {
        return By.xpath("//input[@type='password' and @name='password']");
    }

    @Override
    public By loginButtonSelector() {
        return By.xpath("//input[@value='Log in!' and @type='submit']");
    }

    @Override
    public double zoomLevel() {
        return ZOOM_LEVEL_FOR_SCREENSHOT;
    }

    @Override
    protected By logoutButtonSelector() {
        // Highlight the profile menu to make the logout button interactable
        final By logoutParentBy = By.id("userMenu");
        final WebElement logoutParent = driver.findElement(logoutParentBy);
        ScriptExecutor.moveTo(driver, logoutParent);

        return By.xpath("//a[text()='Log Out']");
    }
}
