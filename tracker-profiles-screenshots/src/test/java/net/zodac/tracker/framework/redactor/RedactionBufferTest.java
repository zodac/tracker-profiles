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

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link RedactionBuffer}.
 */
class RedactionBufferTest {

    @Test
    void givenAllSides_whenOf_thenAllFieldsAssigned() {
        final RedactionBuffer buffer = RedactionBuffer.of(1, 2, 3, 4);

        assertThat(buffer.up()).isEqualTo(1);
        assertThat(buffer.down()).isEqualTo(2);
        assertThat(buffer.left()).isEqualTo(3);
        assertThat(buffer.right()).isEqualTo(4);
    }

    @Test
    void givenDefault_whenAccessed_thenAllZeroes() {
        assertThat(RedactionBuffer.DEFAULT.up()).isZero();
        assertThat(RedactionBuffer.DEFAULT.down()).isZero();
        assertThat(RedactionBuffer.DEFAULT.left()).isZero();
        assertThat(RedactionBuffer.DEFAULT.right()).isZero();
    }

    @Test
    void givenLeftOffset_whenWithLeftOffset_thenOnlyLeftIsSet() {
        final RedactionBuffer buffer = RedactionBuffer.withLeftOffset(7);

        assertThat(buffer.up()).isZero();
        assertThat(buffer.down()).isZero();
        assertThat(buffer.left()).isEqualTo(7);
        assertThat(buffer.right()).isZero();
    }

    @Test
    void givenRightOffset_whenWithRightOffset_thenOnlyRightIsSet() {
        final RedactionBuffer buffer = RedactionBuffer.withRightOffset(9);

        assertThat(buffer.up()).isZero();
        assertThat(buffer.down()).isZero();
        assertThat(buffer.left()).isZero();
        assertThat(buffer.right()).isEqualTo(9);
    }
}
