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

package net.zodac.tracker.framework.gui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Unit tests for {@link DialogPosition}.
 */
class DialogPositionTest {

    @ParameterizedTest
    @CsvSource({
        "0,0",
        "0,100",
        "100,0",
        "100,100",
        "50,60"
    })
    void givenValidPercentages_whenOf_thenAccessorsReturnGivenValues(final int horizontal, final int vertical) {
        final DialogPosition position = DialogPosition.of(horizontal, vertical);
        assertThat(position.horizontalPercent())
            .isEqualTo(horizontal);
        assertThat(position.verticalPercent())
            .isEqualTo(vertical);
    }

    @ParameterizedTest
    @CsvSource({
        "-1,50",
        "101,50",
        "200,0"
    })
    void givenInvalidHorizontalPercent_whenOf_thenIllegalArgumentExceptionThrown(final int horizontal, final int vertical) {
        assertThatThrownBy(() -> DialogPosition.of(horizontal, vertical))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("horizontalPercent");
    }

    @ParameterizedTest
    @CsvSource({
        "50,-1",
        "50,101",
        "0,200"
    })
    void givenInvalidVerticalPercent_whenOf_thenIllegalArgumentExceptionThrown(final int horizontal, final int vertical) {
        assertThatThrownBy(() -> DialogPosition.of(horizontal, vertical))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("verticalPercent");
    }

    @Test
    void givenDefault_whenOfDefault_thenLeftAlignedAtSixtyPercent() {
        final DialogPosition position = DialogPosition.ofDefault();

        assertThat(position.horizontalPercent()).isZero();
        assertThat(position.verticalPercent()).isEqualTo(60);
    }
}
