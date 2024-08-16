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
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;

/**
 * Implementation of {@link AbstractTrackerHandler} for the {@code BwTorrents} tracker.
 */
@TrackerHandlerType(trackerName = "BwTorrents", accessibility = TrackerAccessibility.PRIVATE)
public class BwTorrentsHandler extends AbstractTrackerHandler {

    /**
     * Default constructor.
     *
     * @param driver a {@link ChromeDriver} used to load web pages and perform UI actions
     */
    public BwTorrentsHandler(final ChromeDriver driver) {
        super(driver);
    }

    @Override
    protected WebElement findUsernameField() {
        return driver.findElement(By.xpath("//input[@name='username' and @type='text' and @placeholder='Username']"));
    }

    @Override
    protected WebElement findPasswordField() {
        return driver.findElement(By.xpath("//input[@name='password' and @type='password' and @placeholder='Password']"));
    }

    @Override
    public WebElement findLoginButton() {
        final By loginElementSelector = By.xpath("//input[@value='LOG IN' and @type='submit']");
        return driver.findElement(loginElementSelector);
    }

    @Override
    protected Collection<By> getRootSelectorsForElementsToBeRedacted() {
        return List.of(
            By.tagName("a")
        );
    }

    @Override
    public WebElement findLogoutButton() {
        final By logoutElementSelector = By.xpath("//div[@id='right-sts-aeon']//a[@title='Logout']");
        return driver.findElement(logoutElementSelector);
    }
}
