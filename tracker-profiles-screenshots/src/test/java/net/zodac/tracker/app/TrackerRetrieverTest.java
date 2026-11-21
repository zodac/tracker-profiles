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

import java.util.List;
import net.zodac.tracker.framework.ExecutionOrder;
import net.zodac.tracker.framework.TrackerCredential;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link TrackerRetriever}.
 */
class TrackerRetrieverTest {

    // Anthelion and 52PT are manual trackers, ABTorrents and Aither are headless
    private static final List<TrackerCredential> TRACKERS = List.of(
        credential("Anthelion"),
        credential("Aither"),
        credential("52PT"),
        credential("ABTorrents"),
        credential("UnknownTracker")
    );

    @Test
    void givenAlphabeticalOrder_whenScheduled_thenTrackersAreOrderedByName() {
        assertThat(scheduledNames(ExecutionOrder.ALPHABETICAL))
            .containsExactly("52PT", "ABTorrents", "Aither", "Anthelion");
    }

    @Test
    void givenHeadlessFirstOrder_whenScheduled_thenHeadlessTrackersAreFirstThenEachTypeByName() {
        assertThat(scheduledNames(ExecutionOrder.HEADLESS_FIRST))
            .containsExactly("ABTorrents", "Aither", "52PT", "Anthelion");
    }

    @Test
    void givenManualFirstOrder_whenScheduled_thenManualTrackersAreFirstThenEachTypeByName() {
        assertThat(scheduledNames(ExecutionOrder.MANUAL_FIRST))
            .containsExactly("52PT", "Anthelion", "ABTorrents", "Aither");
    }

    private static List<String> scheduledNames(final ExecutionOrder executionOrder) {
        return TrackerRetriever.schedule(TRACKERS, executionOrder)
            .stream()
            .map(scheduledTracker -> scheduledTracker.credential().name())
            .toList();
    }

    private static TrackerCredential credential(final String name) {
        return new TrackerCredential(name, "username", "password");
    }
}
