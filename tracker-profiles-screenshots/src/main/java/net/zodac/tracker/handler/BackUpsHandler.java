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
import net.zodac.tracker.framework.annotation.TrackerHandler;
import org.jspecify.annotations.Nullable;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.remote.RemoteWebDriver;

/**
 * Implementation of {@link AbstractTrackerHandler} for the {@code BackUps} tracker.
 */
@TrackerHandler(name = "BackUps", url = "https://back-ups.me/")
public class BackUpsHandler extends AbstractTrackerHandler {

    /**
     * Default constructor.
     *
     * @param driver      a {@link RemoteWebDriver} used to load web pages and perform UI actions
     * @param trackerUrls the URLs to the tracker
     */
    public BackUpsHandler(final RemoteWebDriver driver, final Collection<String> trackerUrls) {
        super(driver, trackerUrls);
    }

    @Override
    public By loginPageSelector() {
        return By.xpath("//a[contains(text(), 'Login')]");
    }

    @Override
    protected By usernameFieldSelector() {
        return By.id("usernameloginphp");
    }

    @Override
    protected By passwordFieldSelector() {
        return By.xpath("//div[@id='main']//input[@name='password' and @type='password']");
    }

    @Nullable
    @Override
    protected By loginButtonSelector() {
        return By.xpath("//div[@id='main']//input[@value='LOGIN' and @type='submit']");
    }

    @Override
    protected By postLoginSelector() {
        return By.id("collapseobj_loginbox");
    }

    @Override
    protected By profilePageSelector() {
        // Click the user dropdown menu bar to make the profile button interactable
        final By profileParentSelector = By.id("quickprofileview");
        final WebElement profileParent = driver.findElement(profileParentSelector);
        clickButton(profileParent);

        return By.xpath("//div[@class='qactions']/a[1]");
    }

    @Override
    public Collection<By> getElementsPotentiallyContainingSensitiveInformation() {
        return List.of(
            By.xpath("//tbody[@id='collapseobj_content2a']/tr[1]/td[1]"), // Email
            By.xpath("//tbody[@id='collapseobj_content2c']/tr[1]/td[1]") // IP address
        );
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * For {@link BackUpsHandler}, after clicking the logout button, a Javascript alert appears, which must be accepted.
     */
    @Override
    public void logout() {
        final By logoutButtonSelector = logoutButtonSelector();
        scriptExecutor.waitForElementToAppear(logoutButtonSelector, DEFAULT_WAIT_FOR_PAGE_LOAD);
        final WebElement logoutButton = driver.findElement(logoutButtonSelector);
        clickButton(logoutButton);

        // After clicking logout, a Chrome alert appears - find and click 'Yes'
        scriptExecutor.acceptAlert();

        scriptExecutor.waitForPageToLoad(DEFAULT_WAIT_FOR_PAGE_LOAD);
        scriptExecutor.waitForElementToAppear(postLogoutElementSelector(), DEFAULT_WAIT_FOR_PAGE_LOAD);
    }

    @Override
    protected By logoutButtonSelector() {
        // Click the user dropdown menu bar to make the logout button interactable
        final By logoutParentSelector = By.id("quickprofileview");
        final WebElement logoutParent = driver.findElement(logoutParentSelector);
        clickButton(logoutParent);

        return By.xpath("//div[@class='qactions']/a[2]");
    }
}
