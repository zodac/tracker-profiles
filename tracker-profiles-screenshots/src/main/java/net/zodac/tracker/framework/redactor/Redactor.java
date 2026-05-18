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

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import org.openqa.selenium.WebElement;

/**
 * Interface defining available redaction methods. These can be used prior to taking a screenshot of a tracker profile page to hide sensitive user
 * information.
 */
public interface Redactor {

    /**
     * Redacts the entire {@link WebElement}.
     *
     * @param element     the {@link WebElement} to redact
     * @param description the {@link String} to describe what the sensitive information is
     * @param buffer      the {@link RedactionBuffer} defining the pixel expansion on each side of the redaction, if needed
     * @return the number of elements redacted
     */
    int redact(WebElement element, String description, RedactionBuffer buffer);

    /**
     * Redacts the user's email address.
     *
     * @param element the {@link WebElement} containing the user's email
     * @param buffer  the {@link RedactionBuffer} defining the pixel expansion on each side of the redaction, if needed
     * @return the number of elements redacted
     */
    int redactEmail(WebElement element, RedactionBuffer buffer);

    /**
     * Redacts the user's IP address.
     *
     * @param element the {@link WebElement} containing the user's IP address
     * @param buffer  the {@link RedactionBuffer} defining the pixel expansion on each side of the redaction, if needed
     * @return the number of elements redacted
     */
    int redactIpAddress(WebElement element, RedactionBuffer buffer);

    /**
     * Redacts the user's IRC passkey.
     *
     * <p>
     * If the element's text begins with a known IRC key prefix (case-insensitive), that prefix is preserved and only the value after it is redacted.
     *
     * @param element the {@link WebElement} to redact
     * @param buffer  the {@link RedactionBuffer} defining the pixel expansion on each side of the redaction, if needed
     * @return the number of elements redacted
     */
    int redactIrcPasskey(WebElement element, RedactionBuffer buffer);

    /**
     * Redacts the user's torrent passkey.
     *
     * <p>
     * If the element's text begins with a known passkey prefix (case-insensitive), that prefix is preserved and only the value after it is redacted.
     *
     * @param element the {@link WebElement} containing the user's passkey
     * @param buffer  the {@link RedactionBuffer} defining the pixel expansion on each side of the redaction, if needed
     * @return the number of elements redacted
     */
    int redactTorrentPasskey(WebElement element, RedactionBuffer buffer);

    /**
     * Undoes all DOM mutations applied by the previous redaction pass, restoring the page to its original state. Safe to call multiple times.
     */
    void undoRedaction();

    /**
     * Loads a JavaScript resource file from the same package as this interface. The thrown {@link IllegalStateException}'s message includes the
     * resolved classpath location so callers can log it without needing to compute it themselves.
     *
     * @param scriptName the name of the script resource
     * @return the script content as a {@link String}
     * @throws IllegalStateException if the resource cannot be found or read
     */
    static String loadScript(final String scriptName) {
        final String resourcePath = Redactor.class.getPackageName().replace('.', '/') + '/' + scriptName;
        try (final InputStream inputStream = Redactor.class.getResourceAsStream(scriptName)) {
            if (inputStream == null) {
                throw new IllegalStateException("Could not find redaction script '%s' on classpath at '/%s'".formatted(scriptName, resourcePath));
            }
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (final IOException e) {
            throw new IllegalStateException("Unable to read redaction script '%s' from '/%s'".formatted(scriptName, resourcePath), e);
        }
    }

    /**
     * Loads multiple JavaScript resource files from the same package as this interface, concatenating them in order with a newline separator.
     *
     * @param scriptNames the names of the script resources, in the order they should be concatenated
     * @return the combined script content as a {@link String}
     * @throws IllegalStateException if any resource cannot be found or read
     */
    static String loadScripts(final Collection<String> scriptNames) {
        final StringBuilder combined = new StringBuilder();
        for (final String scriptName : scriptNames) {
            combined.append(loadScript(scriptName)).append('\n');
        }
        return combined.toString();
    }
}
