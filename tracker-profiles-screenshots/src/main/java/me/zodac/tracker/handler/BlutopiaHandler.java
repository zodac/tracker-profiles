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

import java.util.Collection;
import java.util.List;
import me.zodac.tracker.framework.TrackerAccessibility;
import me.zodac.tracker.framework.TrackerHandlerType;
import me.zodac.tracker.util.ScriptExecutor;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;

/**
 * Implementation of {@link AbstractTrackerHandler} for the {@code Blutopia} tracker.
 */
@TrackerHandlerType(trackerName = "Blutopia", accessibility = TrackerAccessibility.PRIVATE)
public class BlutopiaHandler extends AbstractTrackerHandler {

    private static final double ZOOM_LEVEL_FOR_SCREENSHOT = 0.8D;

    /**
     * Default constructor.
     *
     * @param driver a {@link ChromeDriver} used to load web pages and perform UI actions
     */
    public BlutopiaHandler(final ChromeDriver driver) {
        super(driver);
    }

    @Override
    public By loginButtonSelector() {
        return By.xpath("//button[text()='Login' and @class='auth-form__primary-button']");
    }

    @Override
    public boolean canCookieBannerBeCleared() {
        final WebElement alertElement = driver.findElement(By.className("alert__content"));
        final WebElement cookieButton = alertElement.findElement(By.tagName("button"));
        cookieButton.click();

        // Move the mouse, or else a dropdown menu is highlighted and covers some of the page
        ScriptExecutor.moveToOrigin(driver);
        return true;
    }

    @Override
    public double zoomLevelForScreenshot() {
        return ZOOM_LEVEL_FOR_SCREENSHOT;
    }

    @Override
    protected Collection<By> getElementsPotentiallyContainingSensitiveInformation() {
        return List.of(
            By.tagName("dd"),
            By.tagName("td")
        );
    }

    @Override
    protected By logoutButtonSelector() {
        // Highlight the nav bar to make the logout button interactable
        final By logoutParentBy = By.xpath("//div[contains(@class, 'top-nav__right')]//li[contains(@class, 'top-nav__dropdown')]");
        final WebElement logoutParent = driver.findElement(logoutParentBy);
        ScriptExecutor.moveTo(driver, logoutParent);

        return By.xpath("//form[@role='form' and @method='POST']//button[@type='submit']");
    }
}
