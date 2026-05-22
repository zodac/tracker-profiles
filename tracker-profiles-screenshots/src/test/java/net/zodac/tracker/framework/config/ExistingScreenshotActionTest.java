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

package net.zodac.tracker.framework.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Unit tests for {@link ExistingScreenshotAction}.
 */
class ExistingScreenshotActionTest {

    @ParameterizedTest
    @CsvSource({
        "CREATE_ANOTHER,CREATE_ANOTHER",
        "create_another,CREATE_ANOTHER",
    })
    void givenValidInput_whenGetAnyCase_thenMatchingActionReturned(final String input, final ExistingScreenshotAction expected) {
        assertThat(ExistingScreenshotAction.get(input))
            .isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "",
        "unknown",
        "create",
        "skip_now"
    })
    void givenUnknownInput_whenGet_thenNullReturned(final String input) {
        assertThat(ExistingScreenshotAction.get(input))
            .isNull();
    }
}
