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
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.remote.RemoteWebDriver;

/**
 * Common implementation of {@link AbstractTrackerHandler} for {@code Gazelle}-based trackers.
 */
@TrackerHandler(name = "Orpheus", url = "https://orpheus.network/")
@TrackerHandler(name = "Redacted", url = "https://redacted.sh/")
@TrackerHandler(name = "SecretCinema", url = "https://secret-cinema.pw/")
@TrackerHandler(name = "UHDBits", url = "https://uhdbits.org/")
public class GazelleHandler extends AbstractTrackerHandler {

    /**
     * Default constructor.
     *
     * @param driver      a {@link RemoteWebDriver} used to load web pages and perform UI actions
     * @param trackerUrls the URLs to the tracker
     */
    public GazelleHandler(final RemoteWebDriver driver, final Collection<String> trackerUrls) {
        super(driver, trackerUrls);
    }

    @Override
    public By loginPageSelector() {
        return By.xpath("//a[contains(@href, 'login.php')]");
    }

    @Override
    protected By loginButtonSelector() {
        return By.xpath("//input[@type='submit' and @name='login' and @value='Log in' and @class='submit']");
    }

    @Override
    protected By postLoginSelector() {
        return By.id("userinfo");
    }

    @Override
    protected By profilePageSelector() {
        return By.xpath("//a[@class='username']");
    }

    @Override
    public Collection<By> getElementsPotentiallyContainingSensitiveInformation() {
        return List.of(
            By.xpath("//ul[contains(@class, 'stats')]/li[contains(text(), 'Email:')]/a[1]"), // Email
            By.xpath("//div[@id='footer']/p/a[1]/span") // Footer with last used IP address
        );
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * For {@link GazelleHandler}-based trackers, sometimes the logout button is not visible until the link to the user's profile is hovered over.
     * This is not required for all trackers, but since it is a simple mouse move, we can execute it for all trackers and then return the appropriate
     * {@link By} selector.
     *
     * @return the {@link By} selector for the logout button
     */
    @Override
    protected By logoutButtonSelector() {
        // Highlight the profile menu to make the logout button interactable
        final By logoutParentSelector = By.id("userinfo_username");
        final WebElement logoutParent = driver.findElement(logoutParentSelector);
        scriptExecutor.moveTo(logoutParent);

        return By.xpath("//li[@id='nav_logout']//a[1]");
    }
}
