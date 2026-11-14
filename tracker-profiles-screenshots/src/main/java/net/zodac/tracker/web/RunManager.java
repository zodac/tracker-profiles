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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;
import net.zodac.tracker.app.ExecutionListener;
import net.zodac.tracker.app.ScreenshotOrchestrator;
import net.zodac.tracker.app.TrackerRetriever;
import net.zodac.tracker.framework.ExecutionCancellation;
import net.zodac.tracker.framework.ExitState;
import net.zodac.tracker.framework.TrackerCredential;
import net.zodac.tracker.framework.TrackerType;
import net.zodac.tracker.framework.config.ApplicationConfiguration;
import net.zodac.tracker.framework.config.Configuration;
import net.zodac.tracker.framework.config.ConfigurationOption;
import net.zodac.tracker.framework.driver.DriverPool;
import net.zodac.tracker.framework.gui.DisplayValidator;
import net.zodac.tracker.framework.input.UserInputRequests;
import net.zodac.tracker.framework.input.UserInputResponse;
import net.zodac.tracker.framework.progress.TrackerStep;
import net.zodac.tracker.framework.redactor.RedactionType;
import net.zodac.tracker.web.log.WebLogAppender;
import net.zodac.tracker.web.screenshot.ScreenshotFile;
import net.zodac.tracker.web.storage.ApplicationDatabase;
import net.zodac.tracker.web.storage.StoredTrackerCredential;
import net.zodac.tracker.web.storage.StoredTrackerResult;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.Configurator;
import org.jspecify.annotations.Nullable;

/**
 * Executes screenshots in the background when requested by the web UI, ensuring only one execution runs at a time, and tracks its progress.
 *
 * <p>
 * An execution can be cancelled with {@link #cancel()}, which stops it without stopping the application. A new execution cannot be started until
 * a cancelled one has fully stopped.
 *
 * <p>
 * Settings cannot be changed while an execution is in progress (see {@link #isExecutionInProgress()}), so an execution always uses the
 * {@link Configuration} loaded when it started.
 *
 * <p>
 * An execution either includes every enabled tracker ({@link #start()}), or only the trackers that failed in the previous execution
 * ({@link #startFailedTrackers()}). The profile screenshots of the latest successful execution of each tracker are kept across executions (in
 * memory), so they can all be downloaded together (see {@link #latestSuccessfulScreenshots()}).
 */
public final class RunManager implements ExecutionListener {

    private static final Logger LOGGER = LogManager.getLogger();
    private static final Duration INPUT_EXTENSION = Duration.ofMinutes(5L);

    private final Lock lock = new ReentrantLock();
    private final ApplicationDatabase applicationDatabase;
    private final WebLogAppender webLogAppender;

    private RunStatus.State state = RunStatus.State.IDLE;
    // 0 when not set, since PMD flags assigning null to a field
    private long startedAt;
    private long finishedAt;
    private final Set<TrackerType> startedTrackerTypes = new LinkedHashSet<>();
    private final Map<String, TrackerProgressState> trackers = new LinkedHashMap<>();
    // Kept across executions, replaced each time a tracker succeeds, ordered by tracker name
    private final Map<String, List<ScreenshotFile>> latestSuccessfulScreenshots = new TreeMap<>();
    private @Nullable Thread executionThread;

    private RunManager(final ApplicationDatabase applicationDatabase, final WebLogAppender webLogAppender) {
        this.applicationDatabase = applicationDatabase;
        this.webLogAppender = webLogAppender;
    }

    /**
     * Creates a {@link RunManager}.
     *
     * @param applicationDatabase the {@link ApplicationDatabase} from which trackers and settings are loaded for each execution
     * @param webLogAppender      the {@link WebLogAppender} holding the log messages of each execution, cleared when a new execution starts
     * @return the {@link RunManager}
     */
    public static RunManager create(final ApplicationDatabase applicationDatabase, final WebLogAppender webLogAppender) {
        return new RunManager(applicationDatabase, webLogAppender);
    }

    /**
     * Reloads the {@link Configuration} using the settings overridden in the {@link ApplicationDatabase}, and applies the configured log level.
     *
     * @param applicationDatabase the {@link ApplicationDatabase} holding the overridden settings
     * @return the reloaded {@link ApplicationConfiguration}
     * @throws IllegalArgumentException thrown if the combination of environment variables and overridden settings is invalid
     * @throws SQLException             thrown if the settings cannot be read from the database
     */
    public static ApplicationConfiguration reloadConfiguration(final ApplicationDatabase applicationDatabase) throws SQLException {
        final ApplicationConfiguration applicationConfiguration = Configuration.reload(editableOverrides(applicationDatabase.findAllSettings()));
        applyLogLevel(applicationConfiguration);
        return applicationConfiguration;
    }

    // Only apply settings that can be changed at runtime, ignoring any saved for an option that has since become environment-only (or removed)
    private static Map<String, String> editableOverrides(final Map<String, String> savedSettings) {
        return savedSettings
            .entrySet()
            .stream()
            .filter(setting -> {
                final ConfigurationOption option = ConfigurationOption.find(setting.getKey());
                return option != null && option.editableAtRuntime();
            })
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private static void applyLogLevel(final ApplicationConfiguration applicationConfiguration) {
        Configurator.setRootLevel(Level.toLevel(applicationConfiguration.logLevel(), Level.INFO));
    }

    /**
     * Applies settings that have just been saved in the web UI, by reloading the whole {@link Configuration}. If an execution has started since the
     * settings were saved, they are not applied, since the {@link Configuration} is reloaded when the next execution starts.
     *
     * @param savedSettings every setting saved in the web UI, keyed by environment variable name
     * @throws IllegalArgumentException thrown if the combination of environment variables and saved settings is invalid
     */
    public void applySavedSettings(final Map<String, String> savedSettings) {
        if (isExecutionInProgress()) {
            LOGGER.debug("Saved settings not applied, since an execution is in progress");
            return;
        }

        applyLogLevel(Configuration.reload(editableOverrides(savedSettings)));
        LOGGER.debug("Applied saved settings");
    }

    /**
     * Whether an execution is running or being cancelled, during which settings cannot be changed.
     *
     * @return {@code true} if an execution is in progress
     */
    public boolean isExecutionInProgress() {
        lock.lock();
        try {
            return state == RunStatus.State.RUNNING || state == RunStatus.State.CANCELLING;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Starts a new execution of every enabled tracker in the background, unless one is already running.
     *
     * @return {@code true} if an execution was started, {@code false} if one is already running
     */
    public boolean start() {
        lock.lock();
        try {
            if (state == RunStatus.State.RUNNING || state == RunStatus.State.CANCELLING) {
                return false;
            }

            startExecution(Set.of(), false);
        } finally {
            lock.unlock();
        }
        return true;
    }

    /**
     * Starts a new execution in the background of only the trackers that failed in the current (or most recent) execution, unless an execution is
     * already running or no tracker failed. A failed tracker that has since been disabled (or had its credentials removed) is not executed.
     *
     * @return {@code true} if an execution was started, {@code false} if one is already running or no tracker failed
     */
    public boolean startFailedTrackers() {
        lock.lock();
        try {
            if (state == RunStatus.State.RUNNING || state == RunStatus.State.CANCELLING) {
                return false;
            }

            final Set<String> failedTrackerNames = new LinkedHashSet<>(failedTrackerNames());
            if (failedTrackerNames.isEmpty()) {
                return false;
            }
            startExecution(failedTrackerNames, true);
        } finally {
            lock.unlock();
        }
        return true;
    }

    // Must be called while holding the lock
    private void startExecution(final Set<String> failedTrackerNames, final boolean failedTrackersOnly) {
        state = RunStatus.State.RUNNING;
        startedAt = System.currentTimeMillis();
        finishedAt = 0L;
        startedTrackerTypes.clear();
        trackers.clear();
        webLogAppender.clear();
        ExecutionCancellation.reset();
        executionThread = Thread.ofPlatform()
            .name("web-execution")
            .start(() -> execute(failedTrackerNames, failedTrackersOnly));
    }

    // The trackers that failed in the current (or most recent) execution, only while no execution is in progress
    // Must be called while holding the lock
    private List<String> failedTrackerNames() {
        if (state == RunStatus.State.RUNNING || state == RunStatus.State.CANCELLING) {
            return List.of();
        }
        return trackers.values()
            .stream()
            .filter(tracker -> tracker.state == RunStatus.TrackerState.FAILED)
            .map(tracker -> tracker.name)
            .toList();
    }

    /**
     * Cancels the running execution, without stopping the application. Cancellation is requested (so no new tracker, retry attempt or browser is
     * started), then every browser is quit and the execution thread is interrupted, so that in-progress work fails quickly. Cancellation runs in
     * the background; the {@link RunStatus} is {@link RunStatus.State#CANCELLING} until the execution has stopped, then
     * {@link RunStatus.State#CANCELLED}.
     *
     * @return {@code true} if cancellation was started, {@code false} if no execution is running
     */
    public boolean cancel() {
        final Thread threadToInterrupt;
        lock.lock();
        try {
            if (state != RunStatus.State.RUNNING) {
                return false;
            }
            state = RunStatus.State.CANCELLING;
            threadToInterrupt = executionThread;
        } finally {
            lock.unlock();
        }

        LOGGER.warn("Cancelling execution, closing all browsers");
        ExecutionCancellation.requestCancellation();
        // Stopping browsers can take a few seconds, so is not done on the calling (request) thread
        Thread.ofVirtual()
            .name("web-execution-cancellation")
            .start(() -> {
                DriverPool.terminateAll();
                if (threadToInterrupt != null) {
                    threadToInterrupt.interrupt();
                }
            });
        return true;
    }

    /**
     * Responds to a request waiting for a user input.
     *
     * @param inputId  the {@link UserInputRequests.PendingUserInput#id()}
     * @param response the {@link UserInputResponse}
     * @return {@code true} if the request was waiting for a response
     */
    public boolean respondToInput(final long inputId, final UserInputResponse response) {
        return UserInputRequests.respond(inputId, response);
    }

    /**
     * Gives the user five more minutes to respond to a request waiting for a user input.
     *
     * @param inputId the {@link UserInputRequests.PendingUserInput#id()}
     * @return {@code true} if the request was waiting for a response
     */
    public boolean extendInput(final long inputId) {
        return UserInputRequests.extend(inputId, INPUT_EXTENSION);
    }

    private void execute(final Set<String> failedTrackerNames, final boolean failedTrackersOnly) {
        ExitState exitState = ExitState.FAILURE;
        try {
            exitState = executeTrackers(failedTrackerNames, failedTrackersOnly);
        } catch (final Exception e) {
            if (ExecutionCancellation.isCancelled()) {
                LOGGER.debug("Execution ended by cancellation", e);
            } else {
                LOGGER.debug("Error abruptly ended execution", e);
                LOGGER.error("Error abruptly ended execution: {}", e.getMessage());
            }
        } finally {
            finish(exitState);
        }
    }

    private ExitState executeTrackers(final Set<String> failedTrackerNames, final boolean failedTrackersOnly) throws IOException, SQLException {
        if (failedTrackersOnly) {
            LOGGER.info("Starting execution of the {} tracker(s) that failed in the previous execution", failedTrackerNames.size());
        } else {
            LOGGER.info("Starting execution");
        }
        reloadConfiguration(applicationDatabase).print();

        final List<TrackerCredential> enabledTrackers = applicationDatabase.findAllCredentials()
            .stream()
            .filter(StoredTrackerCredential::enabled)
            .filter(credential -> !failedTrackersOnly || failedTrackerNames.contains(credential.name()))
            .map(StoredTrackerCredential::toTrackerCredential)
            .toList();

        if (failedTrackersOnly && enabledTrackers.size() < failedTrackerNames.size()) {
            LOGGER.warn("{} failed tracker(s) no longer enabled, not executed", failedTrackerNames.size() - enabledTrackers.size());
        }

        final Map<TrackerType, Set<TrackerCredential>> trackersByType = TrackerRetriever.groupByType(enabledTrackers);
        if (!DisplayValidator.isValid(trackersByType)) {
            return ExitState.FAILURE;
        }

        return ScreenshotOrchestrator.start(trackersByType, this);
    }

    private void finish(final ExitState exitState) {
        // Clear any interrupt from cancelling, which is no longer needed (the thread is about to end)
        final boolean wasInterrupted = Thread.interrupted();
        LOGGER.trace("Execution thread was interrupted: {}", wasInterrupted);

        final RunStatus.State finalState;
        lock.lock();
        try {
            if (ExecutionCancellation.isCancelled()) {
                finalState = RunStatus.State.CANCELLED;
            } else {
                finalState = switch (exitState) {
                    case SUCCESS -> RunStatus.State.SUCCESS;
                    case PARTIAL_FAILURE -> RunStatus.State.PARTIAL_FAILURE;
                    case FAILURE -> RunStatus.State.FAILURE;
                };
            }
            state = finalState;
            finishedAt = System.currentTimeMillis();
            // A tracker still in progress was stopped by the cancellation, or by an error that ended the execution
            for (final TrackerProgressState tracker : trackers.values()) {
                if (tracker.state == RunStatus.TrackerState.RUNNING) {
                    tracker.state = RunStatus.TrackerState.CANCELLED;
                    tracker.finishedAt = finishedAt;
                }
            }
        } finally {
            lock.unlock();
        }

        if (finalState == RunStatus.State.CANCELLED) {
            LOGGER.warn("Execution cancelled");
        } else {
            LOGGER.info("Execution finished: {}", exitState);
        }
    }

    /**
     * Retrieves the {@link RunStatus} of the current (or most recent) execution.
     *
     * @return the {@link RunStatus}
     */
    public RunStatus status() {
        final Map<String, String> latestMessages = webLogAppender.latestMessages();
        final Map<String, List<ScreenshotFile>> downloadableScreenshots = existingLatestSuccessfulScreenshots();
        lock.lock();
        try {
            final List<RunStatus.TrackerProgress> shownTrackers = trackers.values()
                .stream()
                .map(tracker -> tracker.toView(latestMessages.getOrDefault(tracker.name, "")))
                .toList();

            return new RunStatus(
                state,
                nullIfUnset(startedAt),
                nullIfUnset(finishedAt),
                shownTrackers.size(),
                startedTrackerTypes.stream().map(Enum::name).toList(),
                shownTrackers,
                failedTrackerNames(),
                downloadableScreenshots.size(),
                downloadableScreenshots.values().stream().mapToInt(List::size).sum(),
                UserInputRequests.pending()
            );
        } finally {
            lock.unlock();
        }
    }

    /**
     * The start time of the current (or most recent) execution.
     *
     * @return the start time as epoch milliseconds, or {@code null} if no execution has been started
     */
    public @Nullable Long executionStartedAt() {
        lock.lock();
        try {
            return nullIfUnset(startedAt);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Retrieves the screenshots of a tracker in the current (or most recent) execution, as listed in its {@link RunStatus.TrackerProgress}.
     *
     * @param trackerName the name of the tracker
     * @return the {@link ScreenshotFile}s of the tracker, or an empty {@link List} if the tracker is not in the execution
     */
    public List<ScreenshotFile> trackerScreenshots(final String trackerName) {
        lock.lock();
        try {
            final TrackerProgressState tracker = trackers.get(trackerName);
            return tracker == null ? List.of() : tracker.shownScreenshots();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Retrieves the profile screenshots of the latest successful execution of every tracker, across all executions since the application started,
     * ordered by tracker name. A tracker that succeeds again replaces its earlier screenshots, while one that fails keeps those of its last success.
     *
     * @return the latest {@link ScreenshotFile}s of each successful tracker that still exist
     */
    public List<ScreenshotFile> latestSuccessfulScreenshots() {
        final List<ScreenshotFile> screenshots = new ArrayList<>();
        existingLatestSuccessfulScreenshots().values().forEach(screenshots::addAll);
        return screenshots;
    }

    // Only the screenshots whose files still exist (they may have been deleted or moved since), by tracker name. The files are checked without
    // holding the lock
    private Map<String, List<ScreenshotFile>> existingLatestSuccessfulScreenshots() {
        final Map<String, List<ScreenshotFile>> latest;
        lock.lock();
        try {
            latest = new TreeMap<>(latestSuccessfulScreenshots);
        } finally {
            lock.unlock();
        }

        final Map<String, List<ScreenshotFile>> existing = new TreeMap<>();
        latest.forEach((trackerName, screenshots) -> {
            final List<ScreenshotFile> existingScreenshots = screenshots
                .stream()
                .filter(screenshot -> Files.isRegularFile(screenshot.path()))
                .toList();
            if (!existingScreenshots.isEmpty()) {
                existing.put(trackerName, existingScreenshots);
            }
        });
        return existing;
    }

    private static @Nullable Long nullIfUnset(final long epochMillis) {
        return epochMillis == 0L ? null : epochMillis;
    }

    @Override
    public void executionStarted(final Map<TrackerType, List<String>> trackerNamesByType) {
        lock.lock();
        try {
            trackerNamesByType.forEach((trackerType, trackerNames) -> {
                for (final String trackerName : trackerNames) {
                    trackers.put(trackerName, new TrackerProgressState(trackerName, trackerType));
                }
            });
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void trackerTypeStarted(final TrackerType trackerType) {
        lock.lock();
        try {
            startedTrackerTypes.add(trackerType);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void trackerStarted(final TrackerType trackerType, final String trackerName) {
        lock.lock();
        try {
            final TrackerProgressState tracker = trackers.computeIfAbsent(trackerName, _ -> new TrackerProgressState(trackerName, trackerType));
            tracker.state = RunStatus.TrackerState.RUNNING;
            tracker.startedAt = System.currentTimeMillis();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void trackerAttemptStarted(final String trackerName, final int attempt) {
        lock.lock();
        try {
            final TrackerProgressState tracker = trackers.get(trackerName);
            if (tracker != null) {
                tracker.attempt = attempt;
            }
        } finally {
            lock.unlock();
        }
        // The latest message of the previous attempt no longer describes what the tracker is doing
        webLogAppender.clearLatestMessage(trackerName);
    }

    @Override
    public void trackerStepCompleted(final String trackerName, final TrackerStep trackerStep) {
        lock.lock();
        try {
            final TrackerProgressState tracker = trackers.get(trackerName);
            if (tracker != null) {
                tracker.stepCompletedAt.put(trackerStep, System.currentTimeMillis());
            }
        } finally {
            lock.unlock();
        }
        // The latest message of the completed step no longer describes what the tracker is doing
        webLogAppender.clearLatestMessage(trackerName);
    }

    @Override
    public void trackerScreenshotSaved(final String trackerName, final RedactionType redactionType, final Path screenshot) {
        lock.lock();
        try {
            final TrackerProgressState tracker = trackers.get(trackerName);
            if (tracker != null) {
                // Replacing the value keeps its position, so the screenshots stay in the order they were first taken
                tracker.screenshots.put(redactionType, ScreenshotFile.ofProfile(redactionType, screenshot, System.currentTimeMillis()));
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void trackerFailureScreenshotSaved(final String trackerName, final Path screenshot) {
        lock.lock();
        try {
            final TrackerProgressState tracker = trackers.get(trackerName);
            if (tracker != null) {
                tracker.failureScreenshot.clear();
                tracker.failureScreenshot.add(ScreenshotFile.ofFailure(screenshot, System.currentTimeMillis()));
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void trackerCompleted(final TrackerType trackerType, final String trackerName, final boolean wasSuccessful) {
        final long completedAt = System.currentTimeMillis();
        lock.lock();
        try {
            final TrackerProgressState tracker = trackers.computeIfAbsent(trackerName, _ -> new TrackerProgressState(trackerName, trackerType));
            tracker.state = wasSuccessful ? RunStatus.TrackerState.SUCCEEDED : RunStatus.TrackerState.FAILED;
            tracker.finishedAt = completedAt;
            if (wasSuccessful) {
                // Deleted once the tracker succeeds
                tracker.failureScreenshot.clear();
                latestSuccessfulScreenshots.put(trackerName, List.copyOf(tracker.screenshots.values()));
            }
        } finally {
            lock.unlock();
        }

        try {
            applicationDatabase.saveResult(new StoredTrackerResult(trackerName, completedAt, wasSuccessful));
        } catch (final SQLException e) {
            LOGGER.debug("Unable to save the result for tracker '{}'", trackerName, e);
            LOGGER.warn("Unable to save the result for tracker '{}': {}", trackerName, e.getMessage());
        }
    }

    // Mutable progress of a tracker in the execution, only accessed while holding the lock
    private static final class TrackerProgressState {

        private final String name;
        private final TrackerType trackerType;
        private final Map<TrackerStep, Long> stepCompletedAt = new EnumMap<>(TrackerStep.class);
        private final Map<RedactionType, ScreenshotFile> screenshots = new LinkedHashMap<>();
        // Only the latest failure screenshot is kept (an empty list when there is none, since PMD flags assigning null to a field)
        private final List<ScreenshotFile> failureScreenshot = new ArrayList<>(1);
        private RunStatus.TrackerState state = RunStatus.TrackerState.WAITING;
        // 0 when not set, since PMD flags assigning null to a field
        private long startedAt;
        private long finishedAt;
        private int attempt;

        private TrackerProgressState(final String name, final TrackerType trackerType) {
            this.name = name;
            this.trackerType = trackerType;
        }

        private RunStatus.TrackerProgress toView(final String latestMessage) {
            return new RunStatus.TrackerProgress(name, trackerType.name(), state, nullIfUnset(startedAt), nullIfUnset(finishedAt), attempt,
                stepCompletedAt.keySet().stream().map(Enum::name).toList(), stepCompletedAtByName(), latestMessage,
                shownScreenshots().stream().map(screenshot -> new RunStatus.Screenshot(screenshot.id(), screenshot.label(), screenshot.fileName(),
                    screenshot.failure(), screenshot.savedAt())).toList());
        }

        // A failed (or cancelled) tracker only shows its failure screenshot, since any profile screenshots are from an incomplete attempt
        private List<ScreenshotFile> shownScreenshots() {
            final List<ScreenshotFile> shown = new ArrayList<>();
            if (state != RunStatus.TrackerState.FAILED && state != RunStatus.TrackerState.CANCELLED) {
                shown.addAll(screenshots.values());
            }
            if (state != RunStatus.TrackerState.SUCCEEDED) {
                shown.addAll(failureScreenshot);
            }
            return shown;
        }

        private Map<String, Long> stepCompletedAtByName() {
            final Map<String, Long> byName = new LinkedHashMap<>();
            stepCompletedAt.forEach((trackerStep, completedAt) -> byName.put(trackerStep.name(), completedAt));
            return byName;
        }
    }
}
