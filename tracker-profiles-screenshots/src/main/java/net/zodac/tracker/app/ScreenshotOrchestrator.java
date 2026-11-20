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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.zodac.tracker.framework.ExecutionCancellation;
import net.zodac.tracker.framework.ExitState;
import net.zodac.tracker.framework.TrackerCredential;
import net.zodac.tracker.framework.TrackerType;
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
     *   <li>Executing screenshots for each {@link TrackerType}, in the order they are declared (headless trackers first, as they need no input)</li>
     *   <li>Collecting and reporting results</li>
     * </ol>
     *
     * @param trackersByType    the trackers to execute, organised by {@link TrackerType}
     * @param executionListener the {@link ExecutionListener} to notify of progress
     * @return the {@link ExitState} of the execution
     * @throws IOException thrown if the output directories cannot be created
     * @see ScreenshotDirectories#createAll()
     */
    public static ExitState start(final Map<TrackerType, Set<TrackerCredential>> trackersByType, final ExecutionListener executionListener)
        throws IOException {
        final int numberOfTrackers = countTrackersToExecute(trackersByType);
        if (numberOfTrackers == 0) {
            LOGGER.error("No trackers selected!");
            return ExitState.FAILURE;
        }

        ScreenshotDirectories.createAll();
        executionListener.executionStarted(namesByType(trackersByType));
        final ResultCollector resultCollector = ResultCollector.start();
        try {
            LOGGER.info("Screenshotting {} tracker{}", numberOfTrackers, StringUtils.pluralise(numberOfTrackers));
            TrackerRetriever.printTrackersInfo(trackersByType);

            for (final TrackerType trackerType : TrackerType.values()) {
                if (ExecutionCancellation.isCancelled()) {
                    break;
                }
                executionListener.trackerTypeStarted(trackerType);
                screenshotTrackerByType(trackerType, trackersByType, resultCollector, executionListener);
            }
        } finally {
            ScreenshotTaker.shutdown();
            DriverPool.shutdown();
        }

        if (ExecutionCancellation.isCancelled()) {
            LOGGER.debug("Execution cancelled, not generating a summary");
            return ExitState.FAILURE;
        }
        return resultCollector.generateSummary(List.of(TrackerType.values()));
    }

    private static int countTrackersToExecute(final Map<TrackerType, Set<TrackerCredential>> trackersByType) {
        return trackersByType
            .values()
            .stream()
            .mapToInt(Set::size)
            .sum();
    }

    private static Map<TrackerType, List<String>> namesByType(final Map<TrackerType, Set<TrackerCredential>> trackersByType) {
        final Map<TrackerType, List<String>> trackerNamesByType = new EnumMap<>(TrackerType.class);
        trackersByType.forEach((trackerType, trackers) ->
            trackerNamesByType.put(trackerType, trackers.stream().map(TrackerCredential::name).toList()));
        return trackerNamesByType;
    }

    private static void screenshotTrackerByType(final TrackerType trackerType, final Map<TrackerType, Set<TrackerCredential>> trackersByType,
        final ResultCollector resultCollector, final ExecutionListener executionListener) {
        if (!trackersByType.containsKey(trackerType)) {
            LOGGER.trace("No trackers of type {}", trackerType);
            return;
        }

        final int numberOfTrackers = trackersByType.getOrDefault(trackerType, Set.of()).size();
        final int effectiveThreadCount = getEffectiveThreadCount(numberOfTrackers);
        DriverPool.initialise(trackerType, effectiveThreadCount);

        LOGGER.info("Executing {} trackers{}", trackerType.formattedName(),
            effectiveThreadCount == 1 ? "" : " (with %d threads)".formatted(effectiveThreadCount));

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

    private static int getEffectiveThreadCount(final int numberOfTrackers) {
        LOGGER.trace("numberOfParallelThreads: {}, numberOfTrackers: {}", Configuration.get().numberOfParallelThreads(), numberOfTrackers);
        return Math.min(Configuration.get().numberOfParallelThreads(), numberOfTrackers);
    }
}
