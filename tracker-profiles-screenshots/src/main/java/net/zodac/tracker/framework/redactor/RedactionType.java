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

import java.util.Arrays;
import java.util.Locale;
import org.jspecify.annotations.Nullable;

/**
 * Enum defining the options for redacting sensitive information on a user profile page.
 */
public enum RedactionType {

    /**
     * No redaction performed.
     */
    NONE,

    /**
     * Cover the sensitive text with a Gaussian blur.
     */
    BLUR,

    /**
     * Cover the sensitive text with a solid box with a title.
     */
    BOX;

    /**
     * Returns the formatted name of this {@link RedactionType}, using title case. For example, {@link #BLUR} returns {@code "Blur"}.
     *
     * @return the formatted name
     */
    public String formattedName() {
        final String name = toString();
        return name.charAt(0) + name.substring(1).toLowerCase(Locale.ROOT);
    }

    /**
     * Retrieve a {@link RedactionType} based on the input {@link String}. The search is case-insensitive.
     *
     * @param input the {@link RedactionType} as a {@link String}
     * @return the matching {@link RedactionType}, or {@code null} if none is found
     */
    @Nullable
    public static RedactionType find(final String input) {
        return Arrays.stream(values())
            .filter(redactionType -> redactionType.toString().equalsIgnoreCase(input))
            .findAny()
            .orElse(null);
    }
}
