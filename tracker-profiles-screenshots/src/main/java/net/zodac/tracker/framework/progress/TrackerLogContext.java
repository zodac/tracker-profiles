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

package net.zodac.tracker.framework.progress;

import java.util.List;
import org.apache.logging.log4j.ThreadContext;

/**
 * Sets the Log4j {@link ThreadContext} of the thread screenshotting a tracker, so that each log message can be attributed to the tracker, the
 * attempt, the {@link TrackerStep} and (optionally) a section within the step, such as the redaction type of a screenshot. The web UI uses these to
 * group the log messages of each tracker.
 */
public final class TrackerLogContext {

    /**
     * The key in the logging context holding the name of the tracker being screenshot.
     */
    public static final String TRACKER_KEY = "tracker";

    /**
     * The key in the logging context holding the current attempt number of the tracker, starting at 1.
     */
    public static final String ATTEMPT_KEY = "attempt";

    /**
     * The key in the logging context holding the name of the {@link TrackerStep} in progress.
     */
    public static final String STEP_KEY = "step";

    /**
     * The key in the logging context holding the section of the {@link TrackerStep} in progress, if any.
     */
    public static final String SECTION_KEY = "section";

    private TrackerLogContext() {

    }

    /**
     * Sets the tracker being screenshot by the current thread.
     *
     * @param trackerName the name of the tracker
     */
    public static void startTracker(final String trackerName) {
        ThreadContext.put(TRACKER_KEY, trackerName);
    }

    /**
     * Sets the attempt in progress, starting at {@link TrackerStep#OPEN_TRACKER} (so messages logged before the first step starts are included in
     * it).
     *
     * @param attempt the attempt number, starting at 1
     */
    public static void startAttempt(final int attempt) {
        ThreadContext.put(ATTEMPT_KEY, String.valueOf(attempt));
        startStep(TrackerStep.OPEN_TRACKER);
    }

    /**
     * Sets the {@link TrackerStep} in progress, removing any section of the previous step.
     *
     * @param trackerStep the {@link TrackerStep}
     */
    public static void startStep(final TrackerStep trackerStep) {
        ThreadContext.put(STEP_KEY, trackerStep.name());
        ThreadContext.remove(SECTION_KEY);
    }

    /**
     * Sets the section of the {@link TrackerStep} in progress.
     *
     * @param section the name of the section, shown as a heading in the web UI
     */
    public static void startSection(final String section) {
        ThreadContext.put(SECTION_KEY, section);
    }

    /**
     * Removes the section of the {@link TrackerStep} in progress.
     */
    public static void endSection() {
        ThreadContext.remove(SECTION_KEY);
    }

    /**
     * Removes the attempt, {@link TrackerStep} and section, once an attempt has finished.
     */
    public static void endAttempt() {
        ThreadContext.removeAll(List.of(ATTEMPT_KEY, STEP_KEY, SECTION_KEY));
    }

    /**
     * Removes all values from the logging context of the current thread, once the tracker has finished.
     */
    public static void clear() {
        ThreadContext.clearAll();
    }
}
