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
     * The tracker has a Cloudflare verification check on login (overrides {@link #MANUAL} and {@link #NON_ENGLISH}).
     */
    CLOUDFLARE_CHECK,

    /**
     * The tracker can be run in headless mode (with no UI).
     */
    HEADLESS,

    /**
     * The tracker requires some manual input (like a Captcha or 2FA login), requiring a browser with the UI enabled.
     */
    MANUAL,

    /**
     * The tracker is not in English and can be manually translated, but this requires a browser with the UI enabled.
     */
    NON_ENGLISH;

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
            .filter(trackerType -> trackerType.toCapitalised().equalsIgnoreCase(input))
            .findAny()
            .orElse(null);
    }

    /**
     * Whether the {@link TrackerType} is enabled for the application.
     *
     * @param trackersByType all user-defined trackers by the {@link TrackerType}
     * @param config         the {@link ApplicationConfiguration}
     * @return {@code true} if the {@link TrackerType} is enabled
     */
    public boolean isEnabled(final Map<TrackerType, Set<TrackerDefinition>> trackersByType, final ApplicationConfiguration config) {
        return trackersByType.containsKey(this) && config.trackerExecutionOrder().contains(this);
    }

    /**
     * Prints the information on the user-defined trackers for this {@link TrackerType}.
     *
     * @param trackersByType all user-defined trackers by the {@link TrackerType}
     * @param config         the {@link ApplicationConfiguration}
     */
    public void printSummary(final Map<TrackerType, Set<TrackerDefinition>> trackersByType, final ApplicationConfiguration config) {
        final Set<TrackerDefinition> trackers = trackersByType.getOrDefault(this, Set.of());
        final int numberOfTrackers = isEnabled(trackersByType, config) ? trackers.size() : 0;

        if (numberOfTrackers != 0) {
            LOGGER.debug(String.format("- %-12s: %d", toCapitalised(), numberOfTrackers));
            for (final TrackerDefinition trackerDefinition : trackers) {
                LOGGER.debug(String.format("\t- %-16s", trackerDefinition.name()));
            }
        }
    }

    /**
     * Capitalises the {@link TrackerType}. Also replaces and {@code _} characters with a {@code -}. Note that the first letter after any {@code -}
     * will also be capitalised.
     *
     * @return the capitalised form of the {@link TrackerType}
     */
    public String toCapitalised() {
        final String[] parts = name().toLowerCase(Locale.getDefault()).split("_");
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
