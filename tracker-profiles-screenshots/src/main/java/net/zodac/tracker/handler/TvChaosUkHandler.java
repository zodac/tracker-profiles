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
import net.zodac.tracker.framework.TrackerHandler;
import net.zodac.tracker.util.ScriptExecutor;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;

/**
 * Implementation of {@link AbstractTrackerHandler} for the {@code TVChaosUK} tracker.
 */
@TrackerHandler(name = "TVChaosUK", needsManualInput = false, url = "https://tvchaosuk.com/")
public class TvChaosUkHandler extends AbstractTrackerHandler {

    private static final double ZOOM_LEVEL_FOR_SCREENSHOT = 0.8D;

    /**
     * Default constructor.
     *
     * @param driver      a {@link ChromeDriver} used to load web pages and perform UI actions
     * @param trackerUrls the URLs to the tracker
     */
    public TvChaosUkHandler(final ChromeDriver driver, final Collection<String> trackerUrls) {
        super(driver, trackerUrls);
    }

    @Override
    public By loginButtonSelector() {
        return By.id("login-button");
    }

    @Override
    protected By postLoginSelector() {
        return By.id("main-content");
    }

    @Override
    protected By profilePageSelector() {
        return By.xpath("//ul[@class='list-inline']//li[1]//a[1]");
    }

    @Override
    public boolean canBannerBeCleared() {
        // Cookie banner
        final WebElement cookieButton = driver.findElement(By.xpath("//button[contains(text(), 'Allow cookies')]"));
        cookieButton.click();

        // Move the mouse, or else a dropdown menu is highlighted and covers some of the page
        ScriptExecutor.moveToOrigin(driver);
        return true;
    }

    @Override
    public double zoomLevel() {
        return ZOOM_LEVEL_FOR_SCREENSHOT;
    }

    @Override
    protected By logoutButtonSelector() {
        // Click the profile menu to make the logout button interactable
        final By logoutParentBy = By.xpath("//ul[contains(@class, 'right-navbar')]//li[2]//a[contains(@class, 'dropdown-toggle')]");
        final WebElement logoutParent = driver.findElement(logoutParentBy);
        logoutParent.click();

        return By.xpath("//a[i[contains(@class, 'fa-sign-out')]]");
    }
}
