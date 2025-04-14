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
import net.zodac.tracker.framework.TrackerHandler;
import net.zodac.tracker.util.ScriptExecutor;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;

/**
 * Implementation of {@link AbstractTrackerHandler} for the {@code SeedPool} tracker.
 */
@TrackerHandler(name = "SeedPool", url = "https://seedpool.org/")
public class SeedPoolHandler extends AbstractTrackerHandler {

    /**
     * Default constructor.
     *
     * @param driver      a {@link ChromeDriver} used to load web pages and perform UI actions
     * @param trackerUrls the URLs to the tracker
     */
    public SeedPoolHandler(final ChromeDriver driver, final Collection<String> trackerUrls) {
        super(driver, trackerUrls);
    }

    @Override
    protected By loginButtonSelector() {
        return By.xpath("//button[@class='auth-form__primary-button' and text()='Login']");
    }

    @Override
    protected By postLoginSelector() {
        return By.xpath("//main[@class='page__home']");
    }

    @Override
    public boolean canBannerBeCleared() {
        // Cookie banner
        final WebElement cookieButton = driver.findElement(By.xpath("//button[contains(@class, 'cookie-consent__agree')]"));
        clickButton(cookieButton);

        // Move the mouse, or else a dropdown menu is highlighted and covers some of the page
        ScriptExecutor.moveToOrigin(driver);
        return true;
    }

    @Override
    protected By profilePageSelector() {
        // Highlight the nav bar to make the profile button interactable
        final By profileParentSelector = By.xpath("//div[contains(@class, 'top-nav__right')]//li[contains(@class, 'top-nav__dropdown')]");
        final WebElement profileParent = driver.findElement(profileParentSelector);
        ScriptExecutor.moveTo(driver, profileParent);

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
        ScriptExecutor.updateCss(driver, headerElement, "position", "static");
        return true;
    }

    @Override
    protected By logoutButtonSelector() {
        // Highlight the nav bar to make the logout button interactable
        final By logoutParentSelector = By.xpath("//div[contains(@class, 'top-nav__right')]//li[contains(@class, 'top-nav__dropdown')]");
        final WebElement logoutParent = driver.findElement(logoutParentSelector);
        ScriptExecutor.moveTo(driver, logoutParent);

        return By.xpath("//form[@role='form' and @method='POST']//button[@type='submit']");
    }
}
