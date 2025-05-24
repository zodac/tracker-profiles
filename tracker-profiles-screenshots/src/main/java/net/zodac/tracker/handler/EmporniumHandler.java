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
import org.openqa.selenium.remote.RemoteWebDriver;

/**
 * Implementation of {@link AbstractTrackerHandler} for the {@code Empornium} tracker.
 */
@TrackerHandler(name = "Empornium", url = {
    "https://www.empornium.is/",
    "https://www.empornium.sx/"
})
public class EmporniumHandler extends AbstractTrackerHandler {

    /**
     *  Default constructor.
     *
     * @param driver      a {@link RemoteWebDriver} used to load web pages and perform UI actions
     * @param trackerUrls the URLs to the tracker
     */
    public EmporniumHandler(final RemoteWebDriver driver, final Collection<String> trackerUrls) {
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
     * For {@link EmporniumHandler}, we close additional sections on the profile page prior to screenshotting. Continues if the section does not
     * exist for the user's profile. The sections to close are:
     *
     * <ul>
     *     <li>Recent snatches</li>
     *     <li>Collages</li>
     *     <li>Uploaded torrents</li>
     * </ul>
     */
    @Override
    protected void additionalActionOnProfilePage() {
        // Reload the page, to ensure the section closing works (JS may have been cancelled earlier)
        driver.navigate().refresh();
        scriptExecutor.waitForPageToLoad(DEFAULT_WAIT_FOR_PAGE_LOAD);

        final List<By> toggleSelectors = List.of(
            By.id("recentsnatchesbutton"), // Recent snatches
            By.id("collagesbutton"), // Collages
            By.id("submitbutton") // Uploaded torrents
        );

        for (final By toggleSelector : toggleSelectors) {
            final Collection<WebElement> sectionToggles = driver.findElements(toggleSelector);
            for (final WebElement sectionToggle : sectionToggles) {
                // Only click the toggle if it is already open
                if (sectionToggle.getText().contains("Hide")) {
                    clickButton(sectionToggle);
                    ScriptExecutor.explicitWait(DEFAULT_WAIT_FOR_TRANSITIONS);
                }
            }
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
        scriptExecutor.moveTo(logoutParent);

        return By.xpath("//li[@id='nav_logout']//a[1]");
    }
}
