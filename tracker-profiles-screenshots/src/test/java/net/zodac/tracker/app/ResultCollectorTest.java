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

package net.zodac.tracker.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashSet;
import java.util.Set;
import net.zodac.tracker.framework.ExitState;
import net.zodac.tracker.framework.TrackerType;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ResultCollector}.
 */
class ResultCollectorTest {

    private static final Set<TrackerType> ALL_TYPES_ORDER = orderedSet(TrackerType.HEADLESS, TrackerType.MANUAL);

    @Test
    void givenStartFactory_whenInvoked_thenCollectorIsCreated() {
        final ResultCollector collector = ResultCollector.start();

        assertThat(collector).isNotNull();
    }

    @Test
    void givenNoResults_whenGenerateSummary_thenFailureReturned() {
        final ResultCollector collector = new ResultCollector(System.nanoTime());

        assertThat(collector.generateSummary(ALL_TYPES_ORDER))
            .isEqualTo(ExitState.FAILURE);
    }

    @Test
    void givenAllSuccessfulResults_whenGenerateSummary_thenSuccessReturned() {
        final ResultCollector collector = new ResultCollector(System.nanoTime());
        collector.addResult(TrackerType.HEADLESS, "Alpha", true);
        collector.addResult(TrackerType.HEADLESS, "Beta", true);
        collector.addResult(TrackerType.MANUAL, "Gamma", true);

        assertThat(collector.generateSummary(ALL_TYPES_ORDER))
            .isEqualTo(ExitState.SUCCESS);
    }

    @Test
    void givenAllFailedResults_whenGenerateSummary_thenFailureReturned() {
        final ResultCollector collector = new ResultCollector(System.nanoTime());
        collector.addResult(TrackerType.HEADLESS, "Alpha", false);
        collector.addResult(TrackerType.MANUAL, "Gamma", false);

        assertThat(collector.generateSummary(ALL_TYPES_ORDER))
            .isEqualTo(ExitState.FAILURE);
    }

    @Test
    void givenMixedResults_whenGenerateSummary_thenPartialFailureReturned() {
        final ResultCollector collector = new ResultCollector(System.nanoTime());
        collector.addResult(TrackerType.HEADLESS, "Alpha", true);
        collector.addResult(TrackerType.HEADLESS, "Beta", false);
        collector.addResult(TrackerType.MANUAL, "Gamma", true);
        collector.addResult(TrackerType.MANUAL, "Delta", false);

        assertThat(collector.generateSummary(ALL_TYPES_ORDER))
            .isEqualTo(ExitState.PARTIAL_FAILURE);
    }

    @Test
    void givenDuplicateSuccessForSameTracker_whenGenerateSummary_thenDuplicateNotDoubleCounted() {
        final ResultCollector collector = new ResultCollector(System.nanoTime());
        collector.addResult(TrackerType.HEADLESS, "Alpha", true);
        collector.addResult(TrackerType.HEADLESS, "Alpha", true);

        // Two adds with the same name should still resolve to "all successful" (only one entry stored)
        assertThat(collector.generateSummary(ALL_TYPES_ORDER))
            .isEqualTo(ExitState.SUCCESS);
    }

    @SafeVarargs
    private static <T> Set<T> orderedSet(final T... values) {
        final Set<T> set = new LinkedHashSet<>();
        for (final T value : values) {
            set.add(value);
        }
        return set;
    }
}
