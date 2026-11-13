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
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import net.zodac.tracker.framework.exception.ExecutionCancelledException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ExecutionCancellation}.
 */
class ExecutionCancellationTest {

    @AfterEach
    void tearDown() {
        ExecutionCancellation.reset();
    }

    @Test
    void givenNoCancellation_whenCheckpoint_thenNoExceptionThrown() {
        assertThat(ExecutionCancellation.isCancelled())
            .isFalse();
        assertThatCode(ExecutionCancellation::throwIfCancelled)
            .doesNotThrowAnyException();
    }

    @Test
    void givenCancellation_whenCheckpoint_thenExceptionThrown() {
        ExecutionCancellation.requestCancellation();

        assertThat(ExecutionCancellation.isCancelled())
            .isTrue();
        assertThatThrownBy(ExecutionCancellation::throwIfCancelled)
            .isInstanceOf(ExecutionCancelledException.class);
    }

    @Test
    void givenCancellation_whenReset_thenNoLongerCancelled() {
        ExecutionCancellation.requestCancellation();
        ExecutionCancellation.reset();

        assertThat(ExecutionCancellation.isCancelled())
            .isFalse();
    }
}
