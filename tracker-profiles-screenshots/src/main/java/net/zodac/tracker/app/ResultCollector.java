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
import java.util.EnumMap;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.locks.ReentrantLock;
import net.zodac.tracker.framework.ExitState;
import net.zodac.tracker.framework.TrackerType;
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
    private final Map<TrackerType, Collection<String>> successfulTrackers = new EnumMap<>(TrackerType.class);
    private final Map<TrackerType, Collection<String>> unsuccessfulTrackers = new EnumMap<>(TrackerType.class);

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
     * @param trackerType   the type of tracker
     * @param trackerName   the name of the tracker
     * @param wasSuccessful whether the screenshot was successful
     */
    void addResult(final TrackerType trackerType, final String trackerName, final boolean wasSuccessful) {
        addResultLock.lock();
        try {
            final Map<TrackerType, Collection<String>> targetMap = wasSuccessful ? successfulTrackers : unsuccessfulTrackers;
            targetMap
                .computeIfAbsent(trackerType, _ -> new TreeSet<>())
                .add(trackerName);
        } finally {
            addResultLock.unlock();
        }
    }

    /**
     * Generates a summary of all results and returns the appropriate {@link ExitState}.
     *
     * @param executedTrackerTypes the {@link TrackerType}s that were executed, in the order they were executed
     * @return the {@link ExitState} based on success/failure counts
     */
    ExitState generateSummary(final Collection<TrackerType> executedTrackerTypes) {
        final int totalSuccessful = successfulTrackers.values()
            .stream()
            .mapToInt(Collection::size)
            .sum();
        final int totalUnsuccessful = unsuccessfulTrackers.values()
            .stream()
            .mapToInt(Collection::size)
            .sum();

        if (totalSuccessful == 0 && totalUnsuccessful == 0) {
            LOGGER.error("");
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

        return handlePartialFailure(executedTrackerTypes, totalSuccessful, totalUnsuccessful);
    }

    private @Nullable ExitState handleFailure(final int totalSuccessful, final int totalUnsuccessful) {
        if (totalSuccessful != 0) {
            return null;
        }

        LOGGER.error("");
        LOGGER.error("{} selected tracker{} failed in {}:", totalUnsuccessful, StringUtils.pluralise(totalUnsuccessful), executionTime());
        unsuccessfulTrackers.forEach((type, trackers) -> {
            LOGGER.error("- {}:", type.formattedName());
            trackers.forEach(name -> LOGGER.error("\t- {}", name));
        });
        return ExitState.FAILURE;
    }

    private @Nullable ExitState handleSuccess(final int totalUnsuccessful, final int totalSuccessful) {
        if (totalUnsuccessful != 0) {
            return null;
        }

        LOGGER.info("");
        LOGGER.info("{} selected tracker{} successfully screenshot in {}", totalSuccessful, StringUtils.pluralise(totalSuccessful), executionTime());
        successfulTrackers.forEach((type, trackers) -> LOGGER.debug("- {} ({}): {}", type.formattedName(), trackers.size(), trackers));
        return ExitState.SUCCESS;
    }

    private ExitState handlePartialFailure(final Collection<TrackerType> executedTrackerTypes, final int totalSuccessful,
        final int totalUnsuccessful) {
        LOGGER.warn("");
        LOGGER.warn("{} tracker{} successfully screenshot, {} tracker{} failed in {}", totalSuccessful, StringUtils.pluralise(totalSuccessful),
            totalUnsuccessful, StringUtils.pluralise(totalUnsuccessful), executionTime());

        for (final TrackerType trackerType : executedTrackerTypes) {
            final Collection<String> trackers = unsuccessfulTrackers.get(trackerType);
            if (trackers == null || trackers.isEmpty()) {
                continue;
            }
            LOGGER.warn("- {}:", trackerType.formattedName());
            trackers.forEach(name -> LOGGER.warn("\t- {}", name));
        }
        return ExitState.PARTIAL_FAILURE;
    }

    private String executionTime() {
        return TimingUtils.toNaturalTime(System.nanoTime() - executionStartNanos);
    }
}
