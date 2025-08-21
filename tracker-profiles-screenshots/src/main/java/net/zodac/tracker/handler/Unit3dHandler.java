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
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.remote.RemoteWebDriver;

/**
 * Common implementation of {@link AbstractTrackerHandler} for {@code UNIT3D}-based trackers.
 */
@TrackerHandler(name = "Aither", url = "https://aither.cc/")
@TrackerHandler(name = "FearNoPeer", url = "https://fearnopeer.com/login") // URL set to login page to bypass Cloudflare verification
@TrackerHandler(name = "HDUnited", url = "https://hd-united.vn/")
@TrackerHandler(name = "LST", url = "https://lst.gg/")
@TrackerHandler(name = "PrivateSilverScreen", url = "https://privatesilverscreen.cc/")
@TrackerHandler(name = "ReelFlix", url = "https://reelflix.xyz/")
@TrackerHandler(name = "SeedPool", url = "https://seedpool.org/")
@TrackerHandler(name = "UploadCX", type = TrackerType.CLOUDFLARE_CHECK, url = "https://upload.cx/") // Cloudflare check on login screen, no redirect
@TrackerHandler(name = "Unwalled", url = "https://unwalled.cc/")
public class Unit3dHandler extends AbstractTrackerHandler {

    /**
     * Default constructor.
     *
     * @param driver      a {@link RemoteWebDriver} used to load web pages and perform UI actions
     * @param trackerUrls the URLs to the tracker
     */
    public Unit3dHandler(final RemoteWebDriver driver, final Collection<String> trackerUrls) {
        super(driver, trackerUrls);
    }

    @Override
    protected By loginButtonSelector() {
        return By.xpath("//button[contains(@class, 'auth-form__primary-button')]");
    }

    @Override
    protected By postLoginSelector() {
        return By.xpath("//main[@class='page__home']");
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * For {@link Unit3dHandler}-based trackers, there is a cookie banner on first log-in.
     *
     * @return {@code true} once the banner is cleared
     */
    @Override
    public boolean canBannerBeCleared() {
        // Cookie banner
        final WebElement cookieButton = driver.findElement(By.xpath("//button[contains(@class, 'cookie-consent__agree')]"));
        clickButton(cookieButton);

        // Move the mouse, or else a dropdown menu is highlighted and covers some of the page
        scriptExecutor.moveToOrigin();
        return true;
    }

    @Override
    protected By profilePageSelector() {
        // Highlight the nav bar to make the profile button interactable
        final By profileParentSelector = By.xpath("//div[contains(@class, 'top-nav__right')]//li[contains(@class, 'top-nav__dropdown')]");
        final WebElement profileParent = driver.findElement(profileParentSelector);
        scriptExecutor.moveTo(profileParent);

        return By.xpath("//a[@class='top-nav__username']");
    }

    @Override
    public Collection<By> getElementsPotentiallyContainingSensitiveInformation() {
        return List.of(
            By.xpath("//table[@class='data-table']/tbody/tr/td[2]"), // IP address, potentially multiple entries
            By.xpath("//div[dt[text()='E-mail']]/dd[1]") // Email
        );
    }

    @Override
    public boolean hasFixedHeader() {
        final WebElement headerElement = driver.findElement(By.tagName("header"));
        scriptExecutor.updateCss(headerElement, "position", "static");
        return true;
    }

    @Override
    protected By logoutButtonSelector() {
        // Highlight the nav bar to make the logout button interactable
        final By logoutParentSelector = By.xpath("//div[contains(@class, 'top-nav__right')]//li[contains(@class, 'top-nav__dropdown')]");
        final WebElement logoutParent = driver.findElement(logoutParentSelector);
        scriptExecutor.moveTo(logoutParent);

        return By.xpath("//form[@role='form' and @method='POST']//button[@type='submit']");
    }
}
