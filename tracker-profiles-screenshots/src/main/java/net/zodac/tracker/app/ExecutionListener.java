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

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import net.zodac.tracker.framework.TrackerType;
import net.zodac.tracker.framework.progress.TrackerStep;
import net.zodac.tracker.framework.redactor.RedactionType;

/**
 * Receives progress updates from {@link ScreenshotOrchestrator} during an execution. Used by the web UI to display progress. Implementations must
 * be thread-safe, as the per-tracker methods are called concurrently for parallel trackers.
 */
public interface ExecutionListener {

    /**
     * An {@link ExecutionListener} that ignores all updates.
     */
    ExecutionListener NONE = new ExecutionListener() {
    };

    /**
     * Called once the trackers to be executed are known, before any are executed. Every {@link TrackerType} with an enabled tracker is included.
     *
     * @param trackerNamesByType the names of the trackers of each {@link TrackerType}, in the order they are executed
     */
    default void executionStarted(final Map<TrackerType, List<String>> trackerNamesByType) {
        // Do nothing by default
    }

    /**
     * Called when the trackers of a {@link TrackerType} start executing.
     *
     * @param trackerType the {@link TrackerType}
     */
    default void trackerTypeStarted(final TrackerType trackerType) {
        // Do nothing by default
    }

    /**
     * Called when a tracker starts executing.
     *
     * @param trackerType the {@link TrackerType} of the tracker
     * @param trackerName the name of the tracker
     */
    default void trackerStarted(final TrackerType trackerType, final String trackerName) {
        // Do nothing by default
    }

    /**
     * Called at the start of each attempt to screenshot a tracker.
     *
     * @param trackerName the name of the tracker
     * @param attempt     the attempt number, starting at 1
     */
    default void trackerAttemptStarted(final String trackerName, final int attempt) {
        // Do nothing by default
    }

    /**
     * Called the first time a {@link TrackerStep} succeeds for a tracker (a step is not reported again on a later attempt).
     *
     * @param trackerName the name of the tracker
     * @param trackerStep the completed {@link TrackerStep}
     */
    default void trackerStepCompleted(final String trackerName, final TrackerStep trackerStep) {
        // Do nothing by default
    }

    /**
     * Called each time a profile screenshot of a tracker has been saved. A later screenshot for the same {@link RedactionType} (on a retry)
     * replaces the earlier one.
     *
     * @param trackerName   the name of the tracker
     * @param redactionType the {@link RedactionType} of the screenshot
     * @param screenshot    the saved screenshot file
     */
    default void trackerScreenshotSaved(final String trackerName, final RedactionType redactionType, final Path screenshot) {
        // Do nothing by default
    }

    /**
     * Called each time a failure screenshot of a tracker has been saved, replacing any earlier one. Failure screenshots are deleted if the tracker
     * then succeeds.
     *
     * @param trackerName the name of the tracker
     * @param screenshot  the saved failure screenshot file
     */
    default void trackerFailureScreenshotSaved(final String trackerName, final Path screenshot) {
        // Do nothing by default
    }

    /**
     * Called each time a tracker has finished executing.
     *
     * @param trackerType   the {@link TrackerType} of the tracker
     * @param trackerName   the name of the tracker
     * @param wasSuccessful whether the screenshot was successful
     */
    default void trackerCompleted(final TrackerType trackerType, final String trackerName, final boolean wasSuccessful) {
        // Do nothing by default
    }
}
