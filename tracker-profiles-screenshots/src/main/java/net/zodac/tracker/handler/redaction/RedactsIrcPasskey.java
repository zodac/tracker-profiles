/*
 * BSD Zero Clause License
 *
 * Copyright (c) 2024-2026 zodac.net
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

package net.zodac.tracker.handler.redaction;

import java.util.Collection;
import net.zodac.tracker.framework.redactor.RedactionBuffer;
import net.zodac.tracker.framework.redactor.Redactor;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.remote.RemoteWebDriver;

/**
 * Marks an {@link net.zodac.tracker.handler.AbstractTrackerHandler} as having one or many user IRC passkey {@link WebElement}s on the user profile
 * page that must be redacted before taking the screenshot.
 */
@FunctionalInterface
public interface RedactsIrcPasskey extends Redaction {

    /**
     * Redacts the user's IRC passkey from all matched {@link WebElement}s on the user profile page.
     *
     * @param driver   the {@link RemoteWebDriver}
     * @param redactor the {@link Redactor} to apply the redaction
     * @return the number of {@link WebElement}s redacted
     */
    default int redactIrcPasskeyElements(final RemoteWebDriver driver, final Redactor redactor) {
        return ircPasskeyElements()
            .stream()
            .flatMap(rootSelector -> driver.findElements(rootSelector).stream())
            .mapToInt(element -> redactor.redactIrcPasskey(element, ircPasskeyElementBuffer()))
            .sum();
    }

    /**
     * A {@link Collection} of {@link By} selectors for the user's IRC passkey on the profile page.
     *
     * @return {@link By} selectors for IRC passkey HTML elements
     */
    Collection<By> ircPasskeyElements();

    /**
     * The {@link RedactionBuffer} applied when redacting IRC passkey elements. Override to adjust the buffer for a specific tracker.
     *
     * @return the {@link RedactionBuffer} for passkey redaction
     */
    default RedactionBuffer ircPasskeyElementBuffer() {
        return RedactionBuffer.DEFAULT;
    }
}
