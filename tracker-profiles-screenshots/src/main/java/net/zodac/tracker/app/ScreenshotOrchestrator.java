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

import java.io.IOException;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.zodac.tracker.framework.ExecutionCancellation;
import net.zodac.tracker.framework.ExitState;
import net.zodac.tracker.framework.TrackerType;
import net.zodac.tracker.framework.config.Configuration;
import net.zodac.tracker.framework.driver.DriverPool;
import net.zodac.tracker.framework.exception.ExecutionCancelledException;
import net.zodac.tracker.util.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Main orchestrator class which coordinates taking screenshots of the profile page of each enabled tracker.
 */
public final class ScreenshotOrchestrator {

    private static final Logger LOGGER = LogManager.getLogger();

    private ScreenshotOrchestrator() {

    }

    /**
     * Orchestrates the profile screenshot process by performing the following.
     * <ol>
     *   <li>Ensuring the output directory exists</li>
     *   <li>Executing every tracker, up to {@code NUMBER_OF_PARALLEL_THREADS} at once (of any {@link TrackerType}), in the order provided</li>
     *   <li>Collecting and reporting results</li>
     * </ol>
     *
     * @param scheduledTrackers the trackers to execute, in execution order
     * @param executionListener the {@link ExecutionListener} to notify of progress
     * @return the {@link ExitState} of the execution
     * @throws IOException thrown if the output directories cannot be created
     * @see ScreenshotDirectories#createAll()
     * @see TrackerRetriever#schedule(java.util.Collection)
     */
    public static ExitState start(final List<ScheduledTracker> scheduledTrackers, final ExecutionListener executionListener) throws IOException {
        final int numberOfTrackers = scheduledTrackers.size();
        if (numberOfTrackers == 0) {
            LOGGER.error("No trackers selected!");
            return ExitState.FAILURE;
        }

        ScreenshotDirectories.createAll();
        executionListener.executionStarted(scheduledTrackers);
        final ResultCollector resultCollector = ResultCollector.start();
        try {
            final int effectiveThreadCount = Math.min(Configuration.get().numberOfParallelThreads(), numberOfTrackers);
            LOGGER.info("Screenshotting {} tracker{}{}", numberOfTrackers, StringUtils.pluralise(numberOfTrackers),
                effectiveThreadCount == 1 ? "" : " (%d in parallel)".formatted(effectiveThreadCount));
            LOGGER.debug("Trackers: {}", scheduledTrackers.stream().map(ScheduledTracker::name).toList());

            // The first trackers to start each need a browser of their type, so those are started up front
            DriverPool.initialise(effectiveThreadCount, scheduledTrackers.stream().limit(effectiveThreadCount).map(ScheduledTracker::type).toList());
            screenshotTrackers(scheduledTrackers, effectiveThreadCount, resultCollector, executionListener);
        } finally {
            DriverPool.shutdown();
        }

        if (ExecutionCancellation.isCancelled()) {
            LOGGER.debug("Execution cancelled, not generating a summary");
            return ExitState.FAILURE;
        }
        return resultCollector.generateSummary();
    }

    private static void screenshotTrackers(final List<ScheduledTracker> scheduledTrackers, final int threadCount,
        final ResultCollector resultCollector, final ExecutionListener executionListener) {
        final List<Callable<Void>> trackerScreenshotTasks = scheduledTrackers
            .stream()
            .map(scheduledTracker -> (Callable<Void>) () -> {
                screenshotTracker(scheduledTracker, resultCollector, executionListener);
                return null;
            })
            .toList();

        try (final ExecutorService executor = Executors.newFixedThreadPool(threadCount)) {
            executor.invokeAll(trackerScreenshotTasks);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.debug("Parallel execution interrupted", e);
            if (!ExecutionCancellation.isCancelled()) {
                LOGGER.warn("Parallel execution interrupted: {}", e.getMessage());
            }
        }
    }

    // A tracker that is not started, or fails, because the execution was cancelled is not recorded as a result
    private static void screenshotTracker(final ScheduledTracker scheduledTracker, final ResultCollector resultCollector,
        final ExecutionListener executionListener) {
        if (ExecutionCancellation.isCancelled()) {
            return;
        }

        final String trackerName = scheduledTracker.name();
        executionListener.trackerStarted(trackerName);
        final boolean success;
        try {
            success = ProfileScreenshotExecutor.takeScreenshot(scheduledTracker.credential(), executionListener);
        } catch (final ExecutionCancelledException e) {
            LOGGER.debug("Execution cancelled for tracker '{}'", trackerName, e);
            return;
        }

        if (!success && ExecutionCancellation.isCancelled()) {
            return;
        }
        resultCollector.addResult(trackerName, success);
        executionListener.trackerCompleted(trackerName, success);
    }
}
