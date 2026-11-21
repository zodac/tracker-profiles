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

import java.util.Comparator;

/**
 * The order in which trackers are executed (and listed while waiting to be executed). Trackers of the same {@link TrackerType} are always executed
 * by name.
 */
public enum ExecutionOrder {

    /**
     * Every tracker by name, whatever its {@link TrackerType}.
     */
    ALPHABETICAL(Comparator.comparing(type -> 0)),

    /**
     * {@link TrackerType#HEADLESS} trackers first, then {@link TrackerType#MANUAL} trackers.
     */
    HEADLESS_FIRST(Comparator.comparing(type -> type != TrackerType.HEADLESS)),

    /**
     * {@link TrackerType#MANUAL} trackers first (so their prompts are shown as soon as the execution starts), then {@link TrackerType#HEADLESS}
     * trackers.
     */
    MANUAL_FIRST(Comparator.comparing(type -> type != TrackerType.MANUAL));

    private final Comparator<TrackerType> typeOrder;

    ExecutionOrder(final Comparator<TrackerType> typeOrder) {
        this.typeOrder = typeOrder;
    }

    /**
     * The order of the {@link TrackerType}s, before the trackers of each type are ordered by name.
     *
     * @return the {@link Comparator} of {@link TrackerType}s
     */
    public Comparator<TrackerType> typeOrder() {
        return typeOrder;
    }
}
