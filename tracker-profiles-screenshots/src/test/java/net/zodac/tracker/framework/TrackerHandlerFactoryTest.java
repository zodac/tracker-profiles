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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.NoSuchElementException;
import java.util.Optional;
import net.zodac.tracker.framework.annotation.TrackerHandler;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link TrackerHandlerFactory}.
 */
class TrackerHandlerFactoryTest {

    @Test
    void givenUnknownTrackerName_whenFindMatchingHandler_thenEmptyOptionalReturned() {
        assertThat(TrackerHandlerFactory.findMatchingHandler("NotATracker")).isEmpty();
    }

    @Test
    void givenKnownTrackerName_whenFindMatchingHandler_thenMatchingAnnotationReturned() {
        final Optional<TrackerHandler> result = TrackerHandlerFactory.findMatchingHandler("ABTorrents");
        assertThat(result).isPresent();
        assertThat(result.get().name()).isEqualTo("ABTorrents");
        assertThat(result.get().type()).isEqualTo(TrackerType.HEADLESS);
    }

    @Test
    void givenKnownTrackerNameInDifferentCase_whenFindMatchingHandler_thenMatchingAnnotationReturned() {
        assertThat(TrackerHandlerFactory.findMatchingHandler("abtorrents")).isPresent();
    }

    @Test
    void givenUnknownTrackerName_whenGetHandler_thenNoSuchElementExceptionThrown() {
        assertThatThrownBy(() -> TrackerHandlerFactory.getHandler("NotATracker"))
            .isInstanceOf(NoSuchElementException.class)
            .hasMessageContaining("NotATracker");
    }
}
