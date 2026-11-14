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
import java.util.EnumSet;
import java.util.Set;
import java.util.function.Supplier;
import net.zodac.tracker.framework.TrackerCredential;
import net.zodac.tracker.framework.progress.TrackerLogContext;
import net.zodac.tracker.framework.progress.TrackerStep;
import net.zodac.tracker.framework.redactor.RedactionType;

/**
 * The progress of a single tracker in an execution, across all of its attempts. Keeps the logging context (see {@link TrackerLogContext}) and the
 * {@link ExecutionListener} in step, so each {@link TrackerStep} is logged under its own name and reported once, the first time it succeeds.
 *
 * <p>
 * Only used by the thread executing the tracker.
 */
final class TrackerRun {

    private final TrackerCredential trackerCredential;
    private final ExecutionListener executionListener;
    // Retained across attempts, so a step that succeeded in an earlier attempt is not reported again
    private final Set<TrackerStep> completedSteps;

    private TrackerRun(final TrackerCredential trackerCredential, final ExecutionListener executionListener, final Set<TrackerStep> completedSteps) {
        this.trackerCredential = trackerCredential;
        this.executionListener = executionListener;
        this.completedSteps = completedSteps;
    }

    /**
     * Starts the execution of a tracker, setting its name in the logging context of the current thread.
     *
     * @param trackerCredential the tracker being executed
     * @param executionListener the {@link ExecutionListener} to notify of progress
     * @return the {@link TrackerRun}
     */
    static TrackerRun start(final TrackerCredential trackerCredential, final ExecutionListener executionListener) {
        TrackerLogContext.startTracker(trackerCredential.name());
        return new TrackerRun(trackerCredential, executionListener, EnumSet.noneOf(TrackerStep.class));
    }

    /**
     * The tracker being executed.
     *
     * @return the {@link TrackerCredential}
     */
    TrackerCredential trackerCredential() {
        return trackerCredential;
    }

    /**
     * The name of the tracker being executed.
     *
     * @return the tracker name
     */
    String name() {
        return trackerCredential.name();
    }

    /**
     * Starts an attempt, in the logging context and the {@link ExecutionListener}.
     *
     * @param attempt the attempt number, starting at 1
     */
    void startAttempt(final int attempt) {
        executionListener.trackerAttemptStarted(name(), attempt);
        TrackerLogContext.startAttempt(attempt);
    }

    /**
     * Executes a {@link TrackerStep}, with its messages logged under the step, and reports it as completed if it does not throw.
     *
     * @param trackerStep the {@link TrackerStep}
     * @param action      the work of the step
     */
    void step(final TrackerStep trackerStep, final Runnable action) {
        TrackerLogContext.startStep(trackerStep);
        action.run();
        completeStep(trackerStep);
    }

    /**
     * Executes a {@link TrackerStep} that returns a value, with its messages logged under the step, and reports it as completed if it does not throw.
     *
     * @param trackerStep the {@link TrackerStep}
     * @param action      the work of the step
     * @param <T>         the type of the value returned by the step
     * @return the value returned by {@code action}
     */
    <T> T stepWithResult(final TrackerStep trackerStep, final Supplier<T> action) {
        TrackerLogContext.startStep(trackerStep);
        final T result = action.get();
        completeStep(trackerStep);
        return result;
    }

    /**
     * Reports every {@link TrackerStep} as completed, when there is nothing left to do for the tracker.
     */
    void completeAllSteps() {
        for (final TrackerStep trackerStep : TrackerStep.values()) {
            completeStep(trackerStep);
        }
    }

    /**
     * Whether every {@link TrackerStep} has been completed, in this or an earlier attempt.
     *
     * @return {@code true} if all steps are completed
     */
    boolean allStepsCompleted() {
        return completedSteps.size() == TrackerStep.NUMBER_OF_STEPS;
    }

    /**
     * Reports a saved (or existing) profile screenshot.
     *
     * @param redactionType the {@link RedactionType} of the screenshot
     * @param screenshot    the screenshot file
     */
    void screenshotSaved(final RedactionType redactionType, final Path screenshot) {
        executionListener.trackerScreenshotSaved(name(), redactionType, screenshot);
    }

    /**
     * Reports a saved failure screenshot.
     *
     * @param screenshot the screenshot file
     */
    void failureScreenshotSaved(final Path screenshot) {
        executionListener.trackerFailureScreenshotSaved(name(), screenshot);
    }

    private void completeStep(final TrackerStep trackerStep) {
        if (completedSteps.add(trackerStep)) {
            executionListener.trackerStepCompleted(name(), trackerStep);
        }
    }
}
