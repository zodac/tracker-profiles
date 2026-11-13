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

import net.zodac.tracker.framework.TrackerType;

/**
 * Receives progress updates from {@link ScreenshotOrchestrator} during an execution. Used by the web UI to display progress. Implementations must
 * be thread-safe, as {@link #trackerCompleted(TrackerType, String, boolean)} is called concurrently for parallel trackers.
 */
public interface ExecutionListener {

    /**
     * An {@link ExecutionListener} that ignores all updates.
     */
    ExecutionListener NONE = new ExecutionListener() {
        @Override
        public void executionStarted(final int numberOfTrackers) {
            // Do nothing
        }

        @Override
        public void trackerCompleted(final TrackerType trackerType, final String trackerName, final boolean wasSuccessful) {
            // Do nothing
        }
    };

    /**
     * Called once the trackers to be executed are known, before any are executed.
     *
     * @param numberOfTrackers the total number of trackers to be executed
     */
    void executionStarted(int numberOfTrackers);

    /**
     * Called each time a tracker has finished executing.
     *
     * @param trackerType   the {@link TrackerType} of the tracker
     * @param trackerName   the name of the tracker
     * @param wasSuccessful whether the screenshot was successful
     */
    void trackerCompleted(TrackerType trackerType, String trackerName, boolean wasSuccessful);
}
