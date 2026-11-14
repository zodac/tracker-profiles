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
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.zodac.tracker.framework.ExecutionCancellation;
import net.zodac.tracker.framework.ExitState;
import net.zodac.tracker.framework.TrackerCredential;
import net.zodac.tracker.framework.TrackerType;
import net.zodac.tracker.framework.config.ApplicationConfiguration;
import net.zodac.tracker.framework.config.Configuration;
import net.zodac.tracker.framework.driver.DriverPool;
import net.zodac.tracker.framework.exception.ExecutionCancelledException;
import net.zodac.tracker.util.ScreenshotTaker;
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
     *   <li>Executing screenshots for each {@link TrackerType} in {@link ApplicationConfiguration#trackerExecutionOrder()}</li>
     *   <li>Collecting and reporting results</li>
     * </ol>
     *
     * <p>
     * The execution order is read again before each {@link TrackerType} starts, since it can be changed in the web UI during an execution. A
     * {@link TrackerType} is executed at most once, and a {@link TrackerType} removed from the order before it starts is not executed.
     *
     * @param trackersByType    the trackers to execute, organised by {@link TrackerType}
     * @param executionListener the {@link ExecutionListener} to notify of progress
     * @return the {@link ExitState} of the execution
     * @throws IOException thrown if the output directories cannot be created
     * @see ScreenshotDirectories#createAll()
     */
    public static ExitState start(final Map<TrackerType, Set<TrackerCredential>> trackersByType, final ExecutionListener executionListener)
        throws IOException {
        final int numberOfTrackers = countTrackersToExecute(trackersByType, Configuration.get().trackerExecutionOrder());
        if (numberOfTrackers == 0) {
            LOGGER.error("No trackers selected!");
            return ExitState.FAILURE;
        }

        ScreenshotDirectories.createAll();
        executionListener.executionStarted(countByType(trackersByType));
        final ResultCollector resultCollector = ResultCollector.start();
        final Set<TrackerType> executedTrackerTypes = new LinkedHashSet<>();
        try {
            LOGGER.info("Screenshotting {} tracker{}", numberOfTrackers, StringUtils.pluralise(numberOfTrackers));
            TrackerRetriever.printTrackersInfo(trackersByType, Configuration.get().trackerExecutionOrder());

            Optional<TrackerType> nextTrackerType = nextTrackerType(executedTrackerTypes);
            while (nextTrackerType.isPresent() && !ExecutionCancellation.isCancelled()) {
                final TrackerType trackerType = nextTrackerType.get();
                executedTrackerTypes.add(trackerType);
                executionListener.trackerTypeStarted(trackerType);
                screenshotTrackerByType(trackerType, trackersByType, resultCollector, executionListener);
                nextTrackerType = nextTrackerType(executedTrackerTypes);
            }
        } finally {
            ScreenshotTaker.shutdown();
            DriverPool.shutdown();
        }

        if (ExecutionCancellation.isCancelled()) {
            LOGGER.debug("Execution cancelled, not generating a summary");
            return ExitState.FAILURE;
        }
        return resultCollector.generateSummary(executedTrackerTypes);
    }

    // The first type in the (current) execution order that has not yet been executed
    private static Optional<TrackerType> nextTrackerType(final Set<TrackerType> executedTrackerTypes) {
        return Configuration.get().trackerExecutionOrder()
            .stream()
            .filter(trackerType -> !executedTrackerTypes.contains(trackerType))
            .findFirst();
    }

    private static int countTrackersToExecute(final Map<TrackerType, Set<TrackerCredential>> trackersByType,
        final Set<TrackerType> trackerExecutionOrder) {
        return trackerExecutionOrder
            .stream()
            .mapToInt(trackerType -> trackersByType.getOrDefault(trackerType, Set.of()).size())
            .sum();
    }

    private static Map<TrackerType, Integer> countByType(final Map<TrackerType, Set<TrackerCredential>> trackersByType) {
        final Map<TrackerType, Integer> numberOfTrackersByType = new EnumMap<>(TrackerType.class);
        trackersByType.forEach((trackerType, trackers) -> numberOfTrackersByType.put(trackerType, trackers.size()));
        return numberOfTrackersByType;
    }

    private static void screenshotTrackerByType(final TrackerType trackerType, final Map<TrackerType, Set<TrackerCredential>> trackersByType,
        final ResultCollector resultCollector, final ExecutionListener executionListener) {
        if (!trackersByType.containsKey(trackerType)) {
            LOGGER.trace("No trackers of type {}", trackerType);
            return;
        }

        final int numberOfTrackers = trackersByType.getOrDefault(trackerType, Set.of()).size();
        final int effectiveThreadCount = getEffectiveThreadCount(trackerType, numberOfTrackers);
        DriverPool.initialise(trackerType, effectiveThreadCount);

        LOGGER.info("");
        LOGGER.info(">>> Executing {} trackers {}<<<", trackerType.formattedName(),
            effectiveThreadCount == 1 ? "" : "(with %d threads) ".formatted(effectiveThreadCount));
        LOGGER.info("");

        final List<Callable<Void>> trackerScreenshotTasks = new ArrayList<>();
        for (final TrackerCredential tracker : trackersByType.get(trackerType)) {
            trackerScreenshotTasks.add(() -> {
                screenshotTracker(tracker, trackerType, resultCollector, executionListener);
                return null;
            });
        }

        try (final ExecutorService executor = Executors.newFixedThreadPool(effectiveThreadCount)) {
            executor.invokeAll(trackerScreenshotTasks);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.debug("Parallel execution interrupted for {} trackers", trackerType.formattedName(), e);
            if (!ExecutionCancellation.isCancelled()) {
                LOGGER.warn("Parallel execution interrupted for {} trackers: {}", trackerType.formattedName(), e.getMessage());
            }
        }
    }

    // A tracker that is not started, or fails, because the execution was cancelled is not recorded as a result
    private static void screenshotTracker(final TrackerCredential tracker, final TrackerType trackerType, final ResultCollector resultCollector,
        final ExecutionListener executionListener) {
        if (ExecutionCancellation.isCancelled()) {
            return;
        }

        executionListener.trackerStarted(trackerType, tracker.name());
        final boolean success;
        try {
            success = ProfileScreenshotExecutor.takeScreenshot(tracker, executionListener);
        } catch (final ExecutionCancelledException e) {
            LOGGER.debug("Execution cancelled for tracker '{}'", tracker.name(), e);
            return;
        }

        if (!success && ExecutionCancellation.isCancelled()) {
            return;
        }
        resultCollector.addResult(trackerType, tracker.name(), success);
        executionListener.trackerCompleted(trackerType, tracker.name(), success);
    }

    private static int getEffectiveThreadCount(final TrackerType trackerType, final int numberOfTrackers) {
        if (Configuration.get().forceUiBrowser()) {
            LOGGER.debug("Forcing UI browser, parallelism disabled");
            return 1;
        }

        // Currently only supporting parallel execution for HEADLESS trackers
        if (trackerType != TrackerType.HEADLESS) {
            LOGGER.debug("Tracker type is {}, parallelism disabled", trackerType.formattedName());
            return 1;
        }

        LOGGER.trace("numberOfParallelThreads: {}, numberOfTrackers: {}", Configuration.get().numberOfParallelThreads(), numberOfTrackers);
        return Math.min(Configuration.get().numberOfParallelThreads(), numberOfTrackers);
    }
}
