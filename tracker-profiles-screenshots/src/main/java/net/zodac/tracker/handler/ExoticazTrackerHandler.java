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
import net.zodac.tracker.framework.TrackerType;
import net.zodac.tracker.framework.annotation.TrackerHandler;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.remote.RemoteWebDriver;

/**
 * Extension of the {@link AvistazNetworkTrackerHandler} for the {@code ExoticaZ} tracker.
 */
@TrackerHandler(name = "ExoticaZ", type = TrackerType.CLOUDFLARE_CHECK, url = "https://exoticaz.to/")
public class ExoticazTrackerHandler extends AvistazNetworkTrackerHandler {

    /**
     * Default constructor.
     *
     * @param driver      a {@link RemoteWebDriver} used to load web pages and perform UI actions
     * @param trackerUrls the URLs to the tracker
     */
    public ExoticazTrackerHandler(final RemoteWebDriver driver, final Collection<String> trackerUrls) {
        super(driver, trackerUrls);
    }

    @Override
    protected By usernameFieldSelector() {
        return By.id("username_email");
    }

    @Override
    protected By loginButtonSelector() {
        return By.xpath("//button[contains(text(), 'Login')]");
    }

    @Override
    protected By postLoginSelector() {
        return By.id("navbar-dropdown-user");
    }

    @Override
    protected By profilePageSelector() {
        // Click the user dropdown menu bar to make the profile link interactable
        final By profileLinkParentSelector = By.xpath("//div[@id='topNavBar']/ul[2]/li[3]");
        final WebElement profileLinkParent = driver.findElement(profileLinkParentSelector);
        clickButton(profileLinkParent);

        return By.xpath("//div[@id='topNavBar']/ul[2]/li[3]/div[1]/a[1]");
    }

    @Override
    public boolean hasFixedHeader() {
        final WebElement headerElement = driver.findElement(By.xpath("//nav[contains(@class, 'fixed-top')]"));
        scriptExecutor.updateCss(headerElement, "position", "static");
        return true;
    }

    @Override
    protected By logoutButtonSelector() {
        // Click the user dropdown menu bar to make the logout button interactable
        final By logoutParentSelector = By.xpath("//div[@id='topNavBar']/ul[2]/li[3]");
        final WebElement logoutParent = driver.findElement(logoutParentSelector);
        clickButton(logoutParent);
        return By.xpath("//div[@id='topNavBar']/ul[2]/li[3]/div[1]/a[14]");

    }
}
