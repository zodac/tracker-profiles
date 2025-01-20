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

import net.zodac.tracker.framework.TrackerAccessibility;
import net.zodac.tracker.framework.TrackerHandlerType;
import net.zodac.tracker.util.ScriptExecutor;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;

/**
 * Implementation of {@link AbstractTrackerHandler} for the {@code IPTorrents} tracker.
 */
@TrackerHandlerType(trackerName = "IPTorrents", accessibility = TrackerAccessibility.PRIVATE)
public class IpTorrentsHandler extends AbstractTrackerHandler {

    private static final double ZOOM_LEVEL_FOR_SCREENSHOT = 0.67D;

    /**
     * Default constructor.
     *
     * @param driver a {@link ChromeDriver} used to load web pages and perform UI actions
     */
    public IpTorrentsHandler(final ChromeDriver driver) {
        super(driver);
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
        return By.xpath("//input[@value='Sign In' and @type='submit']");
    }

    @Override
    public double zoomLevel() {
        return ZOOM_LEVEL_FOR_SCREENSHOT;
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * For {@link IpTorrentsHandler}, there is none of the normal sensitive data on the profile page. However, there is a table with our passkey. We
     * find the {@literal <}{@code tr}{@literal >} {@link WebElement} which has a {@literal <}{@code th}{@literal >} {@link WebElement} with the text
     * value <b>Passkey</b>. From this {@literal <}{@code tr}{@literal >}, we find the child {@literal <}{@code td}{@literal >}, which needs its
     * content redacted.
     *
     * @see ScriptExecutor#redactInnerTextOf(JavascriptExecutor, WebElement)
     * @see AbstractTrackerHandler#redactElements()
     */
    @Override
    public int redactElements() {
        final int superRedactedElements = super.redactElements();

        final WebElement passkeyValueElement = driver.findElement(By.xpath("//tr[th[text()='Passkey']]/td"));
        ScriptExecutor.redactInnerTextOf(driver, passkeyValueElement);

        return superRedactedElements + 1;
    }

    @Override
    protected By logoutButtonSelector() {
        return By.xpath("//button[div[text()='Log out']]");
    }
}
