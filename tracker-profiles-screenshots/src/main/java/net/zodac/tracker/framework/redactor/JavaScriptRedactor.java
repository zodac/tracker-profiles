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
 * Implementation of {@link Redactor} that executes bundled JavaScript redaction scripts on the page. The visual treatment (coloured box overlay vs.
 * Gaussian blur) is selected by the supplied {@link RedactionType}.
 */
final class JavaScriptRedactor implements Redactor {

    private static final Logger LOGGER = LogManager.getLogger();
    private static final List<String> SCRIPT_NAMES = List.of(
        "redact_common.js", "redact_element.js", "redact_email.js", "redact_ip_address.js", "redact_passkey.js", "undo_redaction.js"
    );

    private static final String IRC_KEY_PREFIX_ALTERNATION = "IRC Key";
    private static final String TORRENT_PASSKEY_PREFIX_ALTERNATION = "Passkey|Pass Key";

    private static final String CALL_ELEMENT_SCRIPT = "window.__redactElement.apply(null, arguments);";
    private static final String CALL_EMAIL_SCRIPT = "window.__redactEmail.apply(null, arguments);";
    private static final String CALL_IP_ADDRESS_SCRIPT = "window.__redactIpAddress.apply(null, arguments);";
    private static final String CALL_PASSKEY_SCRIPT = "window.__redactPasskey.apply(null, arguments);";
    private static final String CALL_UNDO_SCRIPT = "window.__undoRedaction();";

    private final RemoteWebDriver driver;
    private final RedactionType redactionType;

    private JavaScriptRedactor(final RemoteWebDriver driver, final RedactionType redactionType) {
        this.driver = driver;
        this.redactionType = redactionType;
    }

    /**
     * Creates a {@link JavaScriptRedactor} and installs the redaction scripts on the page.
     *
     * @param driver        the {@link RemoteWebDriver}
     * @param redactionType the {@link RedactionType} controlling the visual treatment; must not be {@link RedactionType#NONE}
     * @return the created {@link JavaScriptRedactor}
     * @throws IllegalStateException if any of the bundled JavaScript redaction scripts cannot be loaded from the classpath
     */
    static JavaScriptRedactor create(final RemoteWebDriver driver, final RedactionType redactionType) {
        try {
            final String installScripts = Redactor.loadScripts(SCRIPT_NAMES);
            driver.executeScript(installScripts);
            return new JavaScriptRedactor(driver, redactionType);
        } catch (final IllegalStateException e) {
            LOGGER.error("Failed to load JavaScript redaction scripts: {}", e.getMessage(), e);
            throw e;
        }
    }

    @Override
    public int redact(final WebElement element, final String description, final RedactionBuffer buffer) {
        final RedactionStyle style = styleFor("orange", "white", description);
        driver.executeScript(CALL_ELEMENT_SCRIPT, element, buffer.left(), buffer.up(), buffer.right(), buffer.down(),
            style.bgColor(), style.textColor(), style.label(), style.blurDef(), style.mode());
        return 1;
    }

    @Override
    public int redactEmail(final WebElement element, final RedactionBuffer buffer) {
        final RedactionStyle style = styleFor("blue", "white", "Email");
        driver.executeScript(CALL_EMAIL_SCRIPT, element, buffer.left(), buffer.up(), buffer.right(), buffer.down(),
            style.bgColor(), style.textColor(), style.label(), style.blurDef(), style.mode());
        return 1;
    }

    @Override
    public int redactIpAddress(final WebElement element, final RedactionBuffer buffer) {
        final RedactionStyle style = styleFor("yellow", "black", "IP");
        driver.executeScript(CALL_IP_ADDRESS_SCRIPT, element, buffer.left(), buffer.up(), buffer.right(), buffer.down(),
            style.bgColor(), style.textColor(), style.label(), style.blurDef(), style.mode());
        return 1;
    }

    @Override
    public int redactIrcPasskey(final WebElement element, final RedactionBuffer buffer) {
        final RedactionStyle style = styleFor("gray", "white", "IRC");
        driver.executeScript(CALL_PASSKEY_SCRIPT, element, buffer.left(), buffer.up(), buffer.right(), buffer.down(),
            style.bgColor(), style.textColor(), style.label(), IRC_KEY_PREFIX_ALTERNATION, style.blurDef(), style.mode());
        return 1;
    }

    @Override
    public int redactTorrentPasskey(final WebElement element, final RedactionBuffer buffer) {
        final RedactionStyle style = styleFor("red", "white", "Passkey");
        driver.executeScript(CALL_PASSKEY_SCRIPT, element, buffer.left(), buffer.up(), buffer.right(), buffer.down(),
            style.bgColor(), style.textColor(), style.label(), TORRENT_PASSKEY_PREFIX_ALTERNATION, style.blurDef(), style.mode());
        return 1;
    }

    @Override
    public void undoRedaction() {
        driver.executeScript(CALL_UNDO_SCRIPT);
    }

    private RedactionStyle styleFor(final String bgColor, final String textColor, final String label) {
        return switch (redactionType) {
            case BLUR -> RedactionStyle.blur();
            case BOX -> RedactionStyle.box(bgColor, textColor, label);
            case NONE -> throw new IllegalStateException("NONE redaction type cannot produce a style");
        };
    }
}
