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
import net.zodac.tracker.framework.ExecutionOrder;
import net.zodac.tracker.framework.input.UserInputRequests.PendingUserInput;
import net.zodac.tracker.web.screenshot.ScreenshotFile;
import org.jspecify.annotations.Nullable;

/**
 * A snapshot of the state of the current (or most recent) execution started from the web UI.
 *
 * @param state                   the {@link State} of the execution
 * @param startedAt               the start time of the execution as epoch milliseconds, or {@code null} if never started
 * @param finishedAt              the end time of the execution as epoch milliseconds, or {@code null} if not finished
 * @param totalTrackers           the number of trackers in {@code trackers}
 * @param trackers                every tracker in the execution, in execution order
 * @param failedTrackers          the names of the trackers that failed in the execution, which can be executed again on their own (empty while an
 *                                execution is in progress)
 * @param executionScreenshots    the number of profile screenshots of the trackers that succeeded in the execution, that still exist
 * @param downloads               the screenshots that can be downloaded from the Trackers tab, from the latest screenshots of every tracker across
 *                                all executions, in the order shown
 * @param executionOrder          the {@link ExecutionOrder} of the next execution, in which the enabled trackers are listed before it starts
 * @param pendingInputs           the requests waiting for the user to provide an input
 */
public record RunStatus(
    State state,
    @Nullable Long startedAt,
    @Nullable Long finishedAt,
    int totalTrackers,
    List<TrackerProgress> trackers,
    List<String> failedTrackers,
    int executionScreenshots,
    List<Download> downloads,
    ExecutionOrder executionOrder,
    List<PendingUserInput> pendingInputs
) {

    /**
     * The progress of a single tracker in the execution.
     *
     * @param name              the tracker name
     * @param type              the tracker type
     * @param state             the {@link TrackerState}
     * @param startedAt         when the tracker started executing, as epoch milliseconds, or {@code null} if it has not started
     * @param finishedAt        when the tracker finished executing, as epoch milliseconds, or {@code null} if it has not finished
     * @param estimatedDuration the estimated duration of the tracker in milliseconds, excluding any time spent waiting for a user input (the
     *                          average of its most recent successful executions), or {@code null} if it has not succeeded often enough
     * @param inputWait         the time the tracker has spent waiting for user inputs that have finished, in milliseconds (excluded from its
     *                          duration when it is estimated)
     * @param attempt           the current (or last) attempt, starting at 1, or 0 if it has not started
     * @param completedSteps    the names of the {@link net.zodac.tracker.framework.progress.TrackerStep}s completed so far, in execution order
     * @param stepCompletedAt   when each of the {@code completedSteps} completed, as epoch milliseconds, keyed by step name
     * @param latestMessage     the most recent message of at least {@code INFO} level logged by the tracker, or empty if none
     * @param screenshots       the screenshots of the tracker: its profile screenshots, or its failure screenshot if it failed (while running,
     *                          both)
     */
    public record TrackerProgress(
        String name,
        String type,
        TrackerState state,
        @Nullable Long startedAt,
        @Nullable Long finishedAt,
        @Nullable Long estimatedDuration,
        long inputWait,
        int attempt,
        List<String> completedSteps,
        Map<String, Long> stepCompletedAt,
        String latestMessage,
        List<Screenshot> screenshots
    ) {

    }

    /**
     * A set of screenshots that can be downloaded together from the Trackers tab, with
     * {@code /api/screenshots?type={type}} (see {@link net.zodac.tracker.web.screenshot.ScreenshotDownload}).
     *
     * @param type  the value of the {@code type} query parameter
     * @param label the label shown for the download
     * @param count the number of screenshots in the download that still exist
     */
    public record Download(
        String type,
        String label,
        int count
    ) {

    }

    /**
     * A screenshot of a tracker, retrieved with {@code /api/screenshots?tracker={trackerName}&id={id}}.
     *
     * @param id       the ID of the screenshot within its tracker
     * @param label    the label shown for the screenshot
     * @param fileName the name of the screenshot file
     * @param failure  whether this is a failure screenshot, rather than a profile screenshot
     * @param savedAt  when the screenshot was saved, as epoch milliseconds (changed when a retry replaces the screenshot)
     */
    public record Screenshot(
        String id,
        String label,
        String fileName,
        boolean failure,
        long savedAt
    ) {

        /**
         * Creates a {@link Screenshot} for a {@link ScreenshotFile}.
         *
         * @param screenshotFile the {@link ScreenshotFile}
         * @return the {@link Screenshot}
         */
        public static Screenshot of(final ScreenshotFile screenshotFile) {
            return new Screenshot(screenshotFile.id(), screenshotFile.label(), screenshotFile.fileName(), screenshotFile.failure(),
                screenshotFile.savedAt());
        }
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
