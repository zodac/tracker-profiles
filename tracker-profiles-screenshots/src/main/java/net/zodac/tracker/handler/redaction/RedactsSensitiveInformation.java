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

import java.util.Map;
import net.zodac.tracker.framework.redactor.RedactionBuffer;
import net.zodac.tracker.framework.redactor.Redactor;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.remote.RemoteWebDriver;

/**
 * Marks an {@link net.zodac.tracker.handler.AbstractTrackerHandler} as having one or many miscellaneous sensitive {@link WebElement}s on the user
 * profile page that must be redacted before taking the screenshot.
 */
@FunctionalInterface
public interface RedactsSensitiveInformation extends Redaction {

    /**
     * Redacts each miscellaneous sensitive {@link WebElement} on the user profile page.
     *
     * @param driver   the {@link RemoteWebDriver}
     * @param redactor the {@link Redactor} to apply the redaction
     * @return the number of {@link WebElement}s redacted
     */
    default int redactSensitiveElements(final RemoteWebDriver driver, final Redactor redactor) {
        return sensitiveElements().entrySet()
            .stream()
            .mapToInt(entry -> {
                final String description = entry.getKey();
                final By selector = entry.getValue();
                return driver.findElements(selector)
                    .stream()
                    .mapToInt(element -> redactor.redact(element, description, sensitiveElementBuffer()))
                    .sum();
            })
            .sum();
    }

    /**
     * A {@link Map} of {@link By} selectors for any miscellaneous sensitive elements visible on the profile page, keyed by a description.
     *
     * @return {@link By} selectors for sensitive HTML elements, with a {@link String} describing the content
     */
    Map<String, By> sensitiveElements();

    /**
     * The {@link RedactionBuffer} applied when redacting sensitive elements. Override to adjust the buffer for a specific tracker.
     *
     * @return the {@link RedactionBuffer} for sensitive element redaction
     */
    default RedactionBuffer sensitiveElementBuffer() {
        return RedactionBuffer.DEFAULT;
    }
}
