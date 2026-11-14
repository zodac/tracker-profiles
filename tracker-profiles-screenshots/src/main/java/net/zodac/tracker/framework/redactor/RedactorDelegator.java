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

package net.zodac.tracker.framework.redactor;

import java.util.regex.Pattern;
import net.zodac.tracker.util.BrowserInteractionHelper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.remote.RemoteWebDriver;

/**
 * Implementation of {@link Redactor} that delegates calls to another concrete implementation, based on the provided {@link RedactionType}.
 */
public final class RedactorDelegator implements Redactor {

    private static final Pattern NEWLINE_PATTERN = Pattern.compile("\\r?\\n");
    private static final Logger LOGGER = LogManager.getLogger();

    private final BrowserInteractionHelper browserInteractionHelper;
    private final Redactor redactor;

    private RedactorDelegator(final BrowserInteractionHelper browserInteractionHelper, final Redactor redactor) {
        this.browserInteractionHelper = browserInteractionHelper;
        this.redactor = redactor;
    }

    /**
     * Creates a {@link RedactorDelegator} for the given {@link RedactionType}.
     *
     * @param driver        the {@link RemoteWebDriver}
     * @param redactionType the {@link RedactionType} to use for redaction
     * @return the created {@link RedactorDelegator}
     * @throws IllegalStateException thrown if {@link RedactionType#NONE} is provided
     */
    public static RedactorDelegator create(final RemoteWebDriver driver, final RedactionType redactionType) {
        final Redactor redactor = switch (redactionType) {
            case BLUR, BOX -> JavaScriptRedactor.create(driver, redactionType);
            case NONE -> throw new IllegalStateException("RedactorDelegator should not be created for NONE redaction type");
        };
        return new RedactorDelegator(BrowserInteractionHelper.of(driver), redactor);
    }

    @Override
    public int redact(final WebElement element, final String description, final RedactionBuffer buffer) {
        logElementToBeRedacted(element, description);
        return redactor.redact(element, description, buffer);
    }

    @Override
    public int redactEmail(final WebElement element, final RedactionBuffer buffer) {
        logElementToBeRedacted(element, "email");
        return redactor.redactEmail(element, buffer);
    }

    @Override
    public int redactIpAddress(final WebElement element, final RedactionBuffer buffer) {
        logElementToBeRedacted(element, "IP Address");
        return redactor.redactIpAddress(element, buffer);
    }

    @Override
    public int redactIrcPasskey(final WebElement element, final RedactionBuffer buffer) {
        logElementToBeRedacted(element, "IRC passkey");
        return redactor.redactIrcPasskey(element, buffer);
    }

    @Override
    public int redactTorrentPasskey(final WebElement element, final RedactionBuffer buffer) {
        logElementToBeRedacted(element, "torrent passkey");
        return redactor.redactTorrentPasskey(element, buffer);
    }

    @Override
    public void undoRedaction() {
        redactor.undoRedaction();
    }

    private void logElementToBeRedacted(final WebElement element, final String elementType) {
        final String elementText = browserInteractionHelper.getTextContent(element);
        final String type = elementType.isBlank() ? "" : (" " + elementType);  // Add leading space for the log output only if there is a type

        if (!elementText.isBlank()) {
            LOGGER.info("Found{}: '{}' in <{}>", type, NEWLINE_PATTERN.matcher(elementText).replaceAll(""), element.getTagName());
            return;
        }

        // Possibly an image
        final String elementSrc = element.getAttribute("src");
        if (elementSrc != null && !elementSrc.isBlank()) {
            LOGGER.info("Found: <{}>: {}", element.getTagName(), element);
            return;
        }

        LOGGER.warn("Found invalid text in <{}> {}, unable to check text or value", element.getTagName(), element);
    }
}
