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

import net.zodac.tracker.framework.ExitState;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ResultCollector}.
 */
class ResultCollectorTest {

    @Test
    void givenStartFactory_whenInvoked_thenCollectorIsCreated() {
        final ResultCollector collector = ResultCollector.start();

        assertThat(collector).isNotNull();
    }

    @Test
    void givenNoResults_whenGenerateSummary_thenFailureReturned() {
        final ResultCollector collector = new ResultCollector(System.nanoTime());

        assertThat(collector.generateSummary())
            .isEqualTo(ExitState.FAILURE);
    }

    @Test
    void givenAllSuccessfulResults_whenGenerateSummary_thenSuccessReturned() {
        final ResultCollector collector = new ResultCollector(System.nanoTime());
        collector.addResult("Alpha", true);
        collector.addResult("Beta", true);
        collector.addResult("Gamma", true);

        assertThat(collector.generateSummary())
            .isEqualTo(ExitState.SUCCESS);
    }

    @Test
    void givenAllFailedResults_whenGenerateSummary_thenFailureReturned() {
        final ResultCollector collector = new ResultCollector(System.nanoTime());
        collector.addResult("Alpha", false);
        collector.addResult("Gamma", false);

        assertThat(collector.generateSummary())
            .isEqualTo(ExitState.FAILURE);
    }

    @Test
    void givenMixedResults_whenGenerateSummary_thenPartialFailureReturned() {
        final ResultCollector collector = new ResultCollector(System.nanoTime());
        collector.addResult("Alpha", true);
        collector.addResult("Beta", false);
        collector.addResult("Gamma", true);
        collector.addResult("Delta", false);

        assertThat(collector.generateSummary())
            .isEqualTo(ExitState.PARTIAL_FAILURE);
    }

    @Test
    void givenDuplicateSuccessForSameTracker_whenGenerateSummary_thenDuplicateNotDoubleCounted() {
        final ResultCollector collector = new ResultCollector(System.nanoTime());
        collector.addResult("Alpha", true);
        collector.addResult("Alpha", true);

        // Two adds with the same name should still resolve to "all successful" (only one entry stored)
        assertThat(collector.generateSummary())
            .isEqualTo(ExitState.SUCCESS);
    }
}
