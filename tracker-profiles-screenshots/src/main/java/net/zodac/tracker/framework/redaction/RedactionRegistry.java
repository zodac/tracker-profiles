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

package net.zodac.tracker.framework.redaction;

import java.util.List;
import net.zodac.tracker.framework.redactor.Redactor;
import net.zodac.tracker.handler.AbstractTrackerHandler;
import net.zodac.tracker.handler.redaction.Redaction;
import net.zodac.tracker.handler.redaction.RedactsEmail;
import net.zodac.tracker.handler.redaction.RedactsIpAddress;
import net.zodac.tracker.handler.redaction.RedactsIrcPasskey;
import net.zodac.tracker.handler.redaction.RedactsSensitiveInformation;
import net.zodac.tracker.handler.redaction.RedactsTorrentPasskey;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.remote.RemoteWebDriver;

/**
 * Registry of every {@link Redaction} category, dispatching redaction calls to whichever ones a given {@link AbstractTrackerHandler} implements.
 *
 * <p>
 * Adding a new redaction category is two steps: create a new interface in this package that extends {@link Redaction}, then add a single
 * {@link RedactionEntry} to {@link #ENTRIES} pointing at the interface's selector and redact methods. No changes required in
 * {@link AbstractTrackerHandler}.
 *
 * @see Redaction
 */
public final class RedactionRegistry {

    private static final Logger LOGGER = LogManager.getLogger();

    private static final List<RedactionEntry<?>> ENTRIES = List.of(
        emailEntry(),
        ipAddressEntry(),
        ircPasskeyEntry(),
        torrentPasskeyEntry(),
        sensitiveInformationEntry()
    );

    private static RedactionEntry<RedactsEmail> emailEntry() {
        return RedactionEntry.of(
            RedactsEmail.class,
            RedactsEmail::redactEmailElements,
            redactsEmail -> redactsEmail.emailElements().isEmpty(),
            "email"
        );
    }

    private static RedactionEntry<RedactsIpAddress> ipAddressEntry() {
        return RedactionEntry.of(
            RedactsIpAddress.class,
            RedactsIpAddress::redactIpAddressElements,
            redactsIpAddress -> redactsIpAddress.ipAddressElements().isEmpty(),
            "IP address"
        );
    }

    private static RedactionEntry<RedactsIrcPasskey> ircPasskeyEntry() {
        return RedactionEntry.of(
            RedactsIrcPasskey.class,
            RedactsIrcPasskey::redactIrcPasskeyElements,
            redactsIrcPasskey -> redactsIrcPasskey.ircPasskeyElements().isEmpty(),
            "IRC passkey"
        );
    }

    private static RedactionEntry<RedactsTorrentPasskey> torrentPasskeyEntry() {
        return RedactionEntry.of(
            RedactsTorrentPasskey.class,
            RedactsTorrentPasskey::redactTorrentPasskeyElements,
            redactsTorrentPasskey -> redactsTorrentPasskey.torrentPasskeyElements().isEmpty(),
            "torrent passkey"
        );
    }

    private static RedactionEntry<RedactsSensitiveInformation> sensitiveInformationEntry() {
        return RedactionEntry.of(
            RedactsSensitiveInformation.class,
            RedactsSensitiveInformation::redactSensitiveElements,
            redactsSensitiveInformation -> redactsSensitiveInformation.sensitiveElements().isEmpty(),
            "sensitive information"
        );
    }

    private RedactionRegistry() {

    }

    /**
     * For each registered redaction the {@code handler} implements, invokes the redaction against the user profile page.
     *
     * @param handler  the {@link AbstractTrackerHandler} being screenshot
     * @param driver   the {@link RemoteWebDriver} the page is loaded in
     * @param redactor the {@link Redactor} used to mutate the matched elements
     * @return the total number of elements redacted across all categories
     */
    public static int redactAll(final AbstractTrackerHandler handler, final RemoteWebDriver driver, final Redactor redactor) {
        int total = 0;
        for (final RedactionEntry<?> entry : ENTRIES) {
            if (entry.applies(handler)) {
                LOGGER.debug("\t\t- Redacting {} elements", entry.description());
                total += entry.invoke(handler, driver, redactor);
            }
        }
        return total;
    }

    /**
     * Checks if the {@code handler} implements at least one redaction interface whose selectors are non-empty. Used to short-circuit the redaction
     * phase when there is nothing to redact.
     *
     * @param handler the {@link AbstractTrackerHandler} to inspect
     * @return {@code true} if any registered redaction would match on this handler
     */
    public static boolean hasAnyRedaction(final AbstractTrackerHandler handler) {
        for (final RedactionEntry<?> entry : ENTRIES) {
            if (entry.applies(handler) && !entry.isEmpty(handler)) {
                return true;
            }
        }
        return false;
    }
}
