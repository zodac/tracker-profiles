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

import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.remote.RemoteWebDriver;

/**
 * Implementation of {@link Redactor} that redacts text by covering the impacted {@link WebElement} with a solid, coloured box with a title.
 */
final class BoxRedactor implements Redactor {

    private static final Logger LOGGER = LogManager.getLogger();
    private static final List<String> SCRIPT_NAMES = List.of(
        "redact_element.js", "redact_email.js", "redact_ip_address.js", "redact_passkey.js", "undo_redaction.js"
    );

    private static final String IRC_KEY_PREFIX_ALTERNATION = "IRC Key";
    private static final String TORRENT_PASSKEY_PREFIX_ALTERNATION = "Passkey|Pass Key";

    private static final String CALL_ELEMENT_SCRIPT = "window.__redactElement.apply(null, arguments);";
    private static final String CALL_EMAIL_SCRIPT = "window.__redactEmail.apply(null, arguments);";
    private static final String CALL_IP_ADDRESS_SCRIPT = "window.__redactIpAddress.apply(null, arguments);";
    private static final String CALL_PASSKEY_SCRIPT = "window.__redactPasskey.apply(null, arguments);";
    private static final String CALL_UNDO_SCRIPT = "window.__undoRedaction();";

    private final RemoteWebDriver driver;

    private BoxRedactor(final RemoteWebDriver driver) {
        this.driver = driver;
    }

    /**
     * Creates a {@link BoxRedactor} and installs the redaction scripts on the page.
     *
     * @param driver the {@link RemoteWebDriver}
     * @return the created {@link BoxRedactor}
     * @throws IllegalStateException if any of the bundled JavaScript redaction scripts cannot be loaded from the classpath
     */
    static BoxRedactor create(final RemoteWebDriver driver) {
        try {
            final String installScripts = Redactor.loadScripts(SCRIPT_NAMES);
            driver.executeScript(installScripts);
            return new BoxRedactor(driver);
        } catch (final IllegalStateException e) {
            LOGGER.error("Failed to load JavaScript redaction scripts: {}", e.getMessage(), e);
            throw e;
        }
    }

    @Override
    public int redact(final WebElement element, final String description, final RedactionBuffer buffer) {
        driver.executeScript(CALL_ELEMENT_SCRIPT, element, buffer.left(), buffer.up(), buffer.right(), buffer.down(), "orange", "white",
            description, "", "box");
        return 1;
    }

    @Override
    public int redactEmail(final WebElement element, final RedactionBuffer buffer) {
        driver.executeScript(CALL_EMAIL_SCRIPT, element, buffer.left(), buffer.up(), buffer.right(), buffer.down(), "blue", "white", "Email",
            "", "box");
        return 1;
    }

    @Override
    public int redactIpAddress(final WebElement element, final RedactionBuffer buffer) {
        driver.executeScript(CALL_IP_ADDRESS_SCRIPT, element, buffer.left(), buffer.up(), buffer.right(), buffer.down(), "yellow", "black", "IP",
            "", "box");
        return 1;
    }

    @Override
    public int redactIrcPasskey(final WebElement element, final RedactionBuffer buffer) {
        driver.executeScript(CALL_PASSKEY_SCRIPT, element, buffer.left(), buffer.up(), buffer.right(), buffer.down(), "gray", "IRC",
            IRC_KEY_PREFIX_ALTERNATION, "", "box");
        return 1;
    }

    @Override
    public int redactTorrentPasskey(final WebElement element, final RedactionBuffer buffer) {
        driver.executeScript(CALL_PASSKEY_SCRIPT, element, buffer.left(), buffer.up(), buffer.right(), buffer.down(), "red", "Passkey",
            TORRENT_PASSKEY_PREFIX_ALTERNATION, "", "box");
        return 1;
    }

    @Override
    public void undoRedaction() {
        driver.executeScript(CALL_UNDO_SCRIPT);
    }
}
