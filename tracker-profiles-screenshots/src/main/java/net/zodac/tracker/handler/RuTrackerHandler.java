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
import net.zodac.tracker.framework.TrackerType;
import net.zodac.tracker.framework.annotation.TrackerHandler;
import net.zodac.tracker.util.ScriptExecutor;
import org.openqa.selenium.By;
import org.openqa.selenium.chrome.ChromeDriver;

/**
 * Implementation of {@link AbstractTrackerHandler} for the {@code RUTracker} tracker.
 */
@TrackerHandler(name = "RUTracker", type = TrackerType.NON_ENGLISH, url = {
    "https://rutracker.org/",
    "https://rutracker.net/"
})
public class RuTrackerHandler extends AbstractTrackerHandler {

    /**
     * Default constructor.
     *
     * @param driver      a {@link ChromeDriver} used to load web pages and perform UI actions
     * @param trackerUrls the URLs to the tracker
     */
    public RuTrackerHandler(final ChromeDriver driver, final Collection<String> trackerUrls) {
        super(driver, trackerUrls);
    }

    @Override
    public By loginPageSelector() {
        // Main page actually is not the tracker, so we navigate to the tracker link, which will prompt us to log in
        return By.xpath("//div[@id='main-nav']/ul[1]/li[2]/a[1]");
    }

    @Override
    protected By usernameFieldSelector() {
        return By.xpath("//form[@id='login-form-full']//input[@name='login_username' and @type='text']");
    }

    @Override
    protected By passwordFieldSelector() {
        return By.xpath("//form[@id='login-form-full']//input[@name='login_password' and @type='password']");
    }

    @Override
    protected By loginButtonSelector() {
        return By.xpath("//form[@id='login-form-full']//input[@type='submit' and @name='login']");
    }

    @Override
    protected By postLoginSelector() {
        return By.xpath("//a[@class='menu-root']");
    }

    @Override
    protected By profilePageSelector() {
        return By.id("logged-in-username");
    }

    @Override
    public boolean isNotEnglish(final String username) {
        ScriptExecutor.translatePage(driver, username, null);
        return true;
    }

    @Override
    protected By logoutButtonSelector() {
        return By.xpath("//img[@class='log-out-icon']");
    }
}
