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
import net.zodac.tracker.util.PatternMatcher;
import net.zodac.tracker.util.ScriptExecutor;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.remote.RemoteWebDriver;

/**
 * Implementation of {@link AbstractTrackerHandler} for the {@code Nebulance} tracker.
 */
@TrackerHandler(name = "Nebulance", url = "https://nebulance.io/")
public class NebulanceHandler extends AbstractTrackerHandler {

    private static final String PASSKEY_PREFIX = "Passkey: ";

    /**
     * Default constructor.
     *
     * @param driver      a {@link RemoteWebDriver} used to load web pages and perform UI actions
     * @param trackerUrls the URLs to the tracker
     */
    public NebulanceHandler(final RemoteWebDriver driver, final Collection<String> trackerUrls) {
        super(driver, trackerUrls);
    }

    @Override
    public By loginPageSelector() {
        return By.xpath("//a[text()='Login']");
    }

    @Override
    protected By loginButtonSelector() {
        return By.xpath("//input[@type='submit' and @name='login' and @value='Login' and @class='submit']");
    }

    @Override
    protected By postLoginSelector() {
        return By.id("candyfloss");
    }

    @Override
    protected By profilePageSelector() {
        return By.xpath("//a[contains(@class, 'username')]");
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * For {@link NebulanceHandler}, the user's profile page cannot be scrolled by default. We override the 'body' tag to allow scrolling to occur
     * when taking the screenshot.
     *
     * @see ScriptExecutor#enableScrolling(String)
     */
    @Override
    protected void additionalActionOnProfilePage() {
        scriptExecutor.enableScrolling("body");
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * For {@link NebulanceHandler}, we also need to redact a passkey {@link WebElement}. We find an element with text that is prefixed by
     * {@value #PASSKEY_PREFIX}, signifying a {@link WebElement} with a sensitive passkey. We redact this element by replacing all text with the
     * prefix and {@value PatternMatcher#DEFAULT_REDACTION_TEXT}.
     *
     * @see AbstractTrackerHandler#redactElements()
     * @see ScriptExecutor#redactInnerTextOf(WebElement, String)
     */
    @Override
    public int redactElements() {
        final By passkeyElementSelector = By.xpath(String.format("//ul[contains(@class, 'stats')]/li[contains(text(), '%s')]", PASSKEY_PREFIX));
        final WebElement passkeyElement = driver.findElement(passkeyElementSelector);
        final String passkeyRedactionText = PASSKEY_PREFIX + PatternMatcher.DEFAULT_REDACTION_TEXT;
        scriptExecutor.redactInnerTextOf(passkeyElement, passkeyRedactionText);

        return 1 + super.redactElements();
    }

    @Override
    public Collection<By> getElementsPotentiallyContainingSensitiveInformation() {
        return List.of(
            By.xpath("//ul[contains(@class, 'stats')]/li[contains(text(), 'Email')]/a[1]"), // Email
            By.xpath("//ul[contains(@class, 'stats')]/li[contains(text(), 'Connectable')]/span[1]") // IP address
        );
    }

    @Override
    protected By logoutButtonSelector() {
        return By.xpath("//li[@id='nav_logout']//a[text()='Logout']");
    }
}
