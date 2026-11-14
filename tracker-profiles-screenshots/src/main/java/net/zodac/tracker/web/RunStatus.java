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
import java.util.Map;
import net.zodac.tracker.framework.input.UserInputRequests.PendingUserInput;
import org.jspecify.annotations.Nullable;

/**
 * A snapshot of the state of the current (or most recent) execution started from the web UI.
 *
 * @param state               the {@link State} of the execution
 * @param startedAt           the start time of the execution as epoch milliseconds, or {@code null} if never started
 * @param finishedAt          the end time of the execution as epoch milliseconds, or {@code null} if not finished
 * @param totalTrackers       the number of trackers in {@code trackers}
 * @param startedTrackerTypes the tracker types that have started executing, in order (the last one is in progress while running)
 * @param trackers            every tracker of the tracker types already started, and (while running) of those still in the execution order
 * @param pendingInputs       the requests waiting for the user to provide an input
 */
public record RunStatus(
    State state,
    @Nullable Long startedAt,
    @Nullable Long finishedAt,
    int totalTrackers,
    List<String> startedTrackerTypes,
    List<TrackerProgress> trackers,
    List<PendingUserInput> pendingInputs
) {

    /**
     * The progress of a single tracker in the execution.
     *
     * @param name            the tracker name
     * @param type            the tracker type
     * @param state           the {@link TrackerState}
     * @param startedAt       when the tracker started executing, as epoch milliseconds, or {@code null} if it has not started
     * @param finishedAt      when the tracker finished executing, as epoch milliseconds, or {@code null} if it has not finished
     * @param attempt         the current (or last) attempt, starting at 1, or 0 if it has not started
     * @param completedSteps  the names of the {@link net.zodac.tracker.framework.progress.TrackerStep}s completed so far, in execution order
     * @param stepCompletedAt when each of the {@code completedSteps} completed, as epoch milliseconds, keyed by step name
     * @param latestMessage   the most recent message of at least {@code INFO} level logged by the tracker, or empty if none
     */
    public record TrackerProgress(
        String name,
        String type,
        TrackerState state,
        @Nullable Long startedAt,
        @Nullable Long finishedAt,
        int attempt,
        List<String> completedSteps,
        Map<String, Long> stepCompletedAt,
        String latestMessage
    ) {

    }

    /**
     * The state of a single tracker in an execution.
     */
    public enum TrackerState {

        /**
         * The tracker has not started yet.
         */
        WAITING,

        /**
         * The tracker is being executed.
         */
        RUNNING,

        /**
         * The tracker was successfully screenshot.
         */
        SUCCEEDED,

        /**
         * Every attempt for the tracker failed.
         */
        FAILED,

        /**
         * The tracker was in progress when the execution was cancelled (or ended abruptly).
         */
        CANCELLED
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
