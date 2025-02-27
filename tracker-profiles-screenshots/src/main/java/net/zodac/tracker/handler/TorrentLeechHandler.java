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
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;

/**
 * Implementation of {@link AbstractTrackerHandler} for the {@code TorrentLeech} tracker.
 */
@TrackerHandler(name = "TorrentLeech", needsManualInput = false, url = {
    "https://www.torrentleech.org/",
    "https://www.torrentleech.cc/",
    "https://www.torrentleech.me/",
    "https://www.tleechreload.org/",
    "https://www.tlgetin.cc/"
})
public class TorrentLeechHandler extends AbstractTrackerHandler {

    /**
     * Default constructor.
     *
     * @param driver      a {@link ChromeDriver} used to load web pages and perform UI actions
     * @param trackerUrls the URLs to the tracker
     */
    public TorrentLeechHandler(final ChromeDriver driver, final Collection<String> trackerUrls) {
        super(driver, trackerUrls);
    }

    @Override
    protected By usernameFieldSelector() {
        return By.xpath("//input[@name='username' and @type='text']");
    }

    @Override
    protected By passwordFieldSelector() {
        return By.xpath("//input[@name='password' and @type='password']");
    }

    @Override
    public By loginButtonSelector() {
        return By.xpath("//button[contains(text(), 'Log in')]");
    }

    @Override
    protected By postLoginSelector() {
        return By.xpath("//div[contains(@class, 'navbar') and contains(@class, 'loggedin')]");
    }
    @Override
    protected By profilePageSelector() {
        return By.xpath("//span[@class='user_superuser']");
    }

    @Override
    public boolean canBannerBeCleared() {
        // IP address warning banner
        final WebElement cookieButton = driver.findElement(By.xpath("//button[contains(@class, 'close') and @title='Dismiss']"));
        cookieButton.click();

        // Move the mouse, or else a dropdown menu is highlighted and covers some of the page
        ScriptExecutor.moveToOrigin(driver);
        return true;
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * For {@link TorrentLeechHandler}, there is a table with our passkey. We
     * find the {@literal <}{@code tr}{@literal >} {@link WebElement} which has a {@literal <}{@code th}{@literal >} {@link WebElement} with the text
     * value <b>Torrent Passkey</b>. From this {@literal <}{@code tr}{@literal >}, we find the child {@literal <}{@code td}{@literal >}, which needs
     * its content redacted.
     *
     * @see ScriptExecutor#redactInnerTextOf(JavascriptExecutor, WebElement)
     * @see AbstractTrackerHandler#redactElements()
     */
    @Override
    public int redactElements() {
        final int superRedactedElements = super.redactElements();

        final WebElement passkeyValueElement = driver.findElement(By.xpath("//tr[td[text()='Torrent Passkey']]/td[2]"));
        ScriptExecutor.redactInnerTextOf(driver, passkeyValueElement);

        return superRedactedElements + 1;
    }

    @Override
    protected Collection<By> getElementsPotentiallyContainingSensitiveInformation() {
        return List.of(
            By.tagName("td")
        );
    }

    @Override
    protected By logoutButtonSelector() {
        return By.xpath("//span[@title='logout']");
    }
}
