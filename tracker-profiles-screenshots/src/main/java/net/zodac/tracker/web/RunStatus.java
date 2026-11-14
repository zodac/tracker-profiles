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

package net.zodac.tracker.web;

import java.util.List;
import net.zodac.tracker.framework.input.UserInputRequests.PendingUserInput;
import org.jspecify.annotations.Nullable;

/**
 * A snapshot of the state of the current (or most recent) execution started from the web UI.
 *
 * @param state               the {@link State} of the execution
 * @param startedAt           the start time of the execution as epoch milliseconds, or {@code null} if never started
 * @param finishedAt          the end time of the execution as epoch milliseconds, or {@code null} if not finished
 * @param totalTrackers       the number of trackers to be executed, for the tracker types already started and those still in the execution order
 * @param successfulTrackers  the names of the trackers successfully screenshot
 * @param failedTrackers      the names of the trackers that failed
 * @param startedTrackerTypes the tracker types that have started executing, in order (the last one is in progress while running)
 * @param activeTrackers      the trackers currently being executed
 * @param pendingInputs       the requests waiting for the user to provide an input
 */
public record RunStatus(
    State state,
    @Nullable Long startedAt,
    @Nullable Long finishedAt,
    int totalTrackers,
    List<String> successfulTrackers,
    List<String> failedTrackers,
    List<String> startedTrackerTypes,
    List<ActiveTracker> activeTrackers,
    List<PendingUserInput> pendingInputs
) {

    /**
     * A tracker currently being executed.
     *
     * @param name           the tracker name
     * @param type           the tracker type
     * @param startedAt      when the tracker started executing, as epoch milliseconds
     * @param completedSteps the names of the {@link net.zodac.tracker.framework.progress.TrackerStep}s completed so far
     */
    public record ActiveTracker(String name, String type, long startedAt, List<String> completedSteps) {

    }

    /**
     * The state of an execution.
     */
    public enum State {

        /**
         * No execution has been started.
         */
        IDLE,

        /**
         * An execution is in progress.
         */
        RUNNING,

        /**
         * Cancellation of the execution was requested, and is waiting for in-progress work to stop.
         */
        CANCELLING,

        /**
         * The execution was cancelled by the user.
         */
        CANCELLED,

        /**
         * The execution completed and all trackers were successfully screenshot.
         */
        SUCCESS,

        /**
         * The execution completed, but some trackers failed.
         */
        PARTIAL_FAILURE,

        /**
         * The execution failed.
         */
        FAILURE
    }
}
