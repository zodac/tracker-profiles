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
import net.zodac.tracker.framework.annotation.TrackerHandler;
import net.zodac.tracker.util.ScriptExecutor;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;

/**
 * Implementation of {@link AbstractTrackerHandler} for the {@code Empornium} tracker.
 */
@TrackerHandler(name = "Empornium", url = {
    "https://www.empornium.is/",
    "https://www.empornium.sx/"
})
public class EmporniumHandler extends AbstractTrackerHandler {

    /**
     * Constructs an instance of {@link EmporniumHandler}.
     *
     * @param driver      a {@link ChromeDriver} used to load web pages and perform UI actions
     * @param trackerUrls the URLs to the tracker
     */
    public EmporniumHandler(final ChromeDriver driver, final Collection<String> trackerUrls) {
        super(driver, trackerUrls);
    }

    @Override
    public By loginPageSelector() {
        return By.xpath("//a[text()='Login']");
    }

    @Override
    protected By usernameFieldSelector() {
        return By.xpath("//div[@id='username']//input[@name='username']");
    }

    @Override
    protected By passwordFieldSelector() {
        return By.xpath("//div[@id='password']//input[@name='password']");
    }

    @Override
    protected By loginButtonSelector() {
        return By.id("login_button");
    }

    @Override
    protected By postLoginSelector() {
        return By.xpath("//table[contains(@class, 'userinfo_stats')]");
    }

    @Override
    protected By profilePageSelector() {
        return By.xpath("//a[@class='username']");
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * For {@link EmporniumHandler}, we close additional sections on the profile page prior to screenshotting. Continues if no section exists for the
     * user's profile.
     */
    @Override
    protected void additionalActionOnProfilePage() {
        // Reload the page, to ensure the section closing works (JS may have been cancelled earlier)
        driver.navigate().refresh();
        ScriptExecutor.waitForPageToLoad(driver, DEFAULT_WAIT_FOR_PAGE_LOAD);

        final By sectionSelector = By.id("collagesbutton");
        final Collection<WebElement> sections = driver.findElements(sectionSelector);
        for (final WebElement section : sections) {
            clickButton(section);
            ScriptExecutor.explicitWait(DEFAULT_WAIT_FOR_TRANSITIONS);
        }
    }

    @Override
    public Collection<By> getElementsPotentiallyContainingSensitiveInformation() {
        return List.of(
            By.xpath("//ul[contains(@class, 'stats')]/li[contains(text(), 'Email')]/a[1]") // Email
        );
    }

    @Override
    protected By logoutButtonSelector() {
        // Highlight the nav bar to make the logout button interactable
        final By logoutParentSelector = By.id("userinfo_username");
        final WebElement logoutParent = driver.findElement(logoutParentSelector);
        ScriptExecutor.moveTo(driver, logoutParent);

        return By.xpath("//li[@id='nav_logout']//a[1]");
    }
}
