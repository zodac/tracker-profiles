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

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Unit tests for {@link TrackerType}.
 */
class TrackerTypeTest {

    @ParameterizedTest
    @CsvSource({
        "HEADLESS,HEADLESS",
        "headless,HEADLESS",
    })
    void givenValidInput_whenFindAnyCase_thenMatchingTrackerTypeReturned(final String input, final TrackerType expected) {
        assertThat(TrackerType.find(input))
            .isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "",
        "unknown",
        "head",
        "manual_input"
    })
    void givenUnknownInput_whenFind_thenNullReturned(final String input) {
        assertThat(TrackerType.find(input))
            .isNull();
    }

    @ParameterizedTest
    @CsvSource({
        "HEADLESS,Headless",
        "MANUAL,Manual"
    })
    void givenTrackerType_whenFormattedName_thenCapitalisedNameReturned(final TrackerType type, final String expected) {
        assertThat(type.formattedName())
            .isEqualTo(expected);
    }

    @Test
    void givenTrackerType_whenAllValues_thenAllEnumValuesReturned() {
        assertThat(TrackerType.ALL_VALUES)
            .containsExactlyInAnyOrder(TrackerType.HEADLESS, TrackerType.MANUAL);
    }
}
