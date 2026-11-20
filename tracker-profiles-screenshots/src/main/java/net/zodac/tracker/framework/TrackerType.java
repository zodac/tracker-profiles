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

import java.util.Locale;

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
