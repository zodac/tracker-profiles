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
import net.zodac.tracker.framework.annotation.TrackerHandler;
import net.zodac.tracker.util.ScriptExecutor;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.remote.RemoteWebDriver;

/**
 * Implementation of {@link AbstractTrackerHandler} for the {@code TeamOS} tracker.
 *
 * <p>
 * Note that the URL is set to the login page in order to bypass the site redirecting to an advertisement.
 */
@TrackerHandler(name = "TeamOS", url = "https://teamos.xyz/login")
public class TeamOsHandler extends AbstractTrackerHandler {

    /**
     * Default constructor.
     *
     * @param driver      a {@link RemoteWebDriver} used to load web pages and perform UI actions
     * @param trackerUrls the URLs to the tracker
     */
    public TeamOsHandler(final RemoteWebDriver driver, final Collection<String> trackerUrls) {
        super(driver, trackerUrls);
    }

    @Override
    protected By usernameFieldSelector() {
        return By.xpath("//input[@name='login' and @type='text']");
    }

    @Override
    protected By passwordFieldSelector() {
        return By.xpath("//input[@name='password' and @type='password']");
    }

    @Override
    protected By loginButtonSelector() {
        return By.xpath("//button[@type='submit' and contains(@class, 'button--icon--login')]");
    }

    @Override
    protected By postLoginSelector() {
        return By.xpath("//div[@class='focus-wrap-user']");
    }

    @Override
    protected By profilePageSelector() {
        navigateToUserPage();
        return By.xpath("//div[@class='p-body-sideNav']//a[text()='Your profile']");
    }

    @Override
    protected By logoutButtonSelector() {
        navigateToUserPage();
        return By.xpath("//div[@class='p-body-sideNav']//a[text()='Log out']");
    }

    private void navigateToUserPage() {
        ScriptExecutor.explicitWait(WAIT_FOR_LOGIN_PAGE_LOAD);
        // Click the nav bar to make the profile button interactable
        final By profileParentSelector = By.xpath("//a[contains(@class, 'p-navgroup-link--user')]");
        final WebElement profileParent = driver.findElement(profileParentSelector);
        clickButton(profileParent);
        ScriptExecutor.explicitWait(WAIT_FOR_LOGIN_PAGE_LOAD);

        clickButton(profileParent);
        ScriptExecutor.explicitWait(WAIT_FOR_LOGIN_PAGE_LOAD);
    }

    // TODO: Have a before/after screenshot section, where this tracker's bespoke scrollbar can be explicitly hidden?

    /**
     * {@inheritDoc}
     *
     * <p>
     * For {@link TeamOsHandler}, after logout we are redirected to the homepage, not the login page. While we actually ignore this page during login
     * (due to the fact we are redirected to an advertisement), we need to specify it now to confirm logout.
     */
    @Override
    protected By postLogoutElementSelector() {
        return By.xpath("//a[span[text()='Log in']]");
    }
}
