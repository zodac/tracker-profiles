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
import net.zodac.tracker.framework.AbstractTrackerHandler;
import net.zodac.tracker.framework.TrackerHandler;
import net.zodac.tracker.util.ScriptExecutor;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;

/**
 * Implementation of {@link AbstractTrackerHandler} for the {@code MyAnonaMouse} tracker.
 */
@TrackerHandler(name = "MyAnonaMouse", needsManualInput = false, url = "https://www.myanonamouse.net/")
public class MyAnonaMouseHandler extends AbstractTrackerHandler {

    /**
     * Default constructor.
     *
     * @param driver      a {@link ChromeDriver} used to load web pages and perform UI actions
     * @param trackerUrls the URLs to the tracker
     */
    public MyAnonaMouseHandler(final ChromeDriver driver, final Collection<String> trackerUrls) {
        super(driver, trackerUrls);
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
    protected By postLoginSelector() {
        return By.id("userStat");
    }

    @Override
    protected By profilePageSelector() {
        // Highlight the profile menu to make the logout button interactable
        final By profileDropDownSelector = By.xpath("//li[@class='mmUserStats']//a[@tabindex='0']");
        final WebElement profileDropDown = driver.findElement(profileDropDownSelector);
        ScriptExecutor.moveTo(driver, profileDropDown);
        return By.xpath("//a[@class='myInfo']");
    }

    @Override
    protected Collection<By> getElementsPotentiallyContainingSensitiveInformation() {
        return List.of(
            By.xpath("//div[@class='blockBody']//table//tbody//td[@class='row1']")
        );
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
