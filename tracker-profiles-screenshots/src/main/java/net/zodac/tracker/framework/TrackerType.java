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

package net.zodac.tracker.framework;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jspecify.annotations.Nullable;

/**
 * Enum defining the different types of trackers. This can be used to perform different executions for different trackers.
 */
public enum TrackerType {

    /**
     * The tracker can be run in headless mode (with no UI).
     */
    HEADLESS,

    /**
     * The tracker requires some manual input (like a Captcha or 2FA login), requiring a browser with the UI enabled.
     */
    MANUAL;

    /**
     * All available {@link TrackerType}s.
     *
     * <p>
     * Should be used instead of {@link TrackerType#values()}, as that recalculates the array for each call,
     * while this method uses a static {@link Collection}.
     */
    public static final Collection<TrackerType> ALL_VALUES = List.of(values());

    private static final Logger LOGGER = LogManager.getLogger();

    /**
     * Retrieve a {@link TrackerType} based on the input {@link String}. The search is case-insensitive.
     *
     * @param input the {@link TrackerType} as a {@link String}
     * @return the matching {@link TrackerType}, or {@code null} if none is found
     */
    @Nullable
    public static TrackerType find(final String input) {
        return ALL_VALUES
            .stream()
            .filter(trackerType -> trackerType.formattedName().equalsIgnoreCase(input))
            .findAny()
            .orElse(null);
    }

    /**
     * Prints the information on the user-defined trackers for this {@link TrackerType}.
     *
     * @param trackersByType all user-defined trackers by the {@link TrackerType}
     */
    public void printSummary(final Map<TrackerType, Set<TrackerCredential>> trackersByType) {
        final Set<TrackerCredential> trackers = trackersByType.get(this);
        if (trackers == null) {
            LOGGER.trace("Tracker type {} not in execution order", this);
            return;
        }

        if (!trackers.isEmpty()) {
            LOGGER.info("- %-12s: %d".formatted(formattedName(), trackers.size()));
            for (final TrackerCredential trackerCredential : trackers) {
                LOGGER.debug("\t- %-16s".formatted(trackerCredential.name()));
            }
        }
    }

    /**
     * Formats the name of the {@link TrackerType}. Also capitalises the name and replaces any {@code _} characters with a {@code -}. Note that the
     * first letter after any {@code -} will also be capitalised.
     *
     * @return the formatted name of the {@link TrackerType}
     */
    public String formattedName() {
        final String[] parts = name().toLowerCase(Locale.ROOT).split("_", -1);
        final StringBuilder result = new StringBuilder(name().length());

        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                result.append('-');
            }
            result.append(Character.toUpperCase(parts[i].charAt(0)))
                .append(parts[i].substring(1));
        }

        return result.toString();
    }
}
