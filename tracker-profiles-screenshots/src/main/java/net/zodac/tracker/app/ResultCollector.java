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

import java.util.Collection;
import java.util.TreeSet;
import java.util.concurrent.locks.ReentrantLock;
import net.zodac.tracker.framework.ExitState;
import net.zodac.tracker.util.StringUtils;
import net.zodac.tracker.util.TimingUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jspecify.annotations.Nullable;

/**
 * Collects and reports on the results of screenshot attempts.
 */
final class ResultCollector {

    private static final Logger LOGGER = LogManager.getLogger();

    private final ReentrantLock addResultLock = new ReentrantLock();
    private final Collection<String> successfulTrackers = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
    private final Collection<String> unsuccessfulTrackers = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

    private final long executionStartNanos;

    /**
     * Constructor for {@link ResultCollector}.
     *
     * @param executionStartNanos the start time of the execution, in nanoseconds
     */
    ResultCollector(final long executionStartNanos) {
        this.executionStartNanos = executionStartNanos;
    }

    /**
     * Creates a new instance of {@link ResultCollector}, starting with the current {@link System#nanoTime()}.
     *
     * @return the created {@link ResultCollector}
     */
    static ResultCollector start() {
        return new ResultCollector(System.nanoTime());
    }

    /**
     * Records the result of a screenshot attempt.
     *
     * @param trackerName   the name of the tracker
     * @param wasSuccessful whether the screenshot was successful
     */
    void addResult(final String trackerName, final boolean wasSuccessful) {
        addResultLock.lock();
        try {
            (wasSuccessful ? successfulTrackers : unsuccessfulTrackers).add(trackerName);
        } finally {
            addResultLock.unlock();
        }
    }

    /**
     * Generates a summary of all results and returns the appropriate {@link ExitState}.
     *
     * @return the {@link ExitState} based on success/failure counts
     */
    ExitState generateSummary() {
        final int totalSuccessful = successfulTrackers.size();
        final int totalUnsuccessful = unsuccessfulTrackers.size();

        if (totalSuccessful == 0 && totalUnsuccessful == 0) {
            LOGGER.error("Unexpectedly had no trackers execute");
            return ExitState.FAILURE;
        }

        final ExitState failure = handleFailure(totalSuccessful, totalUnsuccessful);
        if (failure != null) {
            return failure;
        }

        final ExitState success = handleSuccess(totalUnsuccessful, totalSuccessful);
        if (success != null) {
            return success;
        }

        return handlePartialFailure(totalSuccessful, totalUnsuccessful);
    }

    private @Nullable ExitState handleFailure(final int totalSuccessful, final int totalUnsuccessful) {
        if (totalSuccessful != 0) {
            return null;
        }

        LOGGER.error("{} selected tracker{} failed in {}:", totalUnsuccessful, StringUtils.pluralise(totalUnsuccessful), executionTime());
        LOGGER.error("Failed: {}", String.join(", ", unsuccessfulTrackers));
        return ExitState.FAILURE;
    }

    private @Nullable ExitState handleSuccess(final int totalUnsuccessful, final int totalSuccessful) {
        if (totalUnsuccessful != 0) {
            return null;
        }

        LOGGER.info("{} selected tracker{} successfully screenshot in {}", totalSuccessful, StringUtils.pluralise(totalSuccessful), executionTime());
        LOGGER.debug("Succeeded: {}", String.join(", ", successfulTrackers));
        return ExitState.SUCCESS;
    }

    private ExitState handlePartialFailure(final int totalSuccessful, final int totalUnsuccessful) {
        LOGGER.warn("{} tracker{} successfully screenshot, {} tracker{} failed in {}", totalSuccessful, StringUtils.pluralise(totalSuccessful),
            totalUnsuccessful, StringUtils.pluralise(totalUnsuccessful), executionTime());
        LOGGER.warn("Failed: {}", String.join(", ", unsuccessfulTrackers));
        return ExitState.PARTIAL_FAILURE;
    }

    private String executionTime() {
        return TimingUtils.toNaturalTime(System.nanoTime() - executionStartNanos);
    }
}
