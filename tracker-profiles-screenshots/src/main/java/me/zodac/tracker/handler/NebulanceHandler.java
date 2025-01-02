/*
 * BSD Zero Clause License
 *
 * Copyright (c) 2021-2025 zodac.me
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

package me.zodac.tracker.handler;

import java.util.Collection;
import java.util.List;
import me.zodac.tracker.framework.TrackerAccessibility;
import me.zodac.tracker.framework.TrackerHandlerType;
import me.zodac.tracker.util.ScriptExecutor;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;

/**
 * Implementation of {@link AbstractTrackerHandler} for the {@code Nebulance} tracker.
 */
@TrackerHandlerType(trackerName = "Nebulance", accessibility = TrackerAccessibility.PRIVATE)
public class NebulanceHandler extends AbstractTrackerHandler {

    private static final String PASSKEY_PREFIX = "Passkey: ";

    /**
     * Default constructor.
     *
     * @param driver a {@link ChromeDriver} used to load web pages and perform UI actions
     */
    public NebulanceHandler(final ChromeDriver driver) {
        super(driver);
    }

    @Override
    protected By loginButtonSelector() {
        return By.xpath("//input[@type='submit' and @name='login' and @value='Login' and @class='submit']");
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * For {@link MoreThanTvHandler}, we also need to redact a passkey {@link WebElement}. We find an element with text that is prefixed by
     * {@value #PASSKEY_PREFIX}, signifying a {@link WebElement} with a sensitive passkey. We redact this element by replacing all text with the
     * prefix and {@value ScriptExecutor#DEFAULT_REDACTION_TEXT}.
     *
     * @see ScriptExecutor#redactInnerTextOf(JavascriptExecutor, WebElement, String)
     * @see AbstractTrackerHandler#redactElements()
     */
    @Override
    public int redactElements() {
        final int superRedactedElements = super.redactElements();

        final String passkeyRedactionText = PASSKEY_PREFIX + ScriptExecutor.DEFAULT_REDACTION_TEXT;
        final WebElement passkeyElement = driver.findElement(By.xpath(String.format("//li[contains(text(),'%s')]", PASSKEY_PREFIX)));
        ScriptExecutor.redactInnerTextOf(driver, passkeyElement, passkeyRedactionText);

        return superRedactedElements + 1;
    }

    @Override
    protected Collection<By> getElementsPotentiallyContainingSensitiveInformation() {
        return List.of(
            By.tagName("a"),
            By.tagName("span")
        );
    }

    @Override
    protected By logoutButtonSelector() {
        return By.xpath("//li[@id='nav_logout']//a[text()='Logout']");
    }
}
