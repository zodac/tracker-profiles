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
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import net.zodac.tracker.web.storage.ApplicationDatabase;
import net.zodac.tracker.web.storage.StoredTrackerCredential;
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
 */
public final class RunManager implements ExecutionListener {

    private static final Logger LOGGER = LogManager.getLogger();

    private final Lock lock = new ReentrantLock();
    private final ApplicationDatabase applicationDatabase;

    private RunStatus.State state = RunStatus.State.IDLE;
    // 0 when not set, since PMD flags assigning null to a field
    private long startedAt;
    private long finishedAt;
    private int totalTrackers;
    private final List<String> successfulTrackers = new ArrayList<>();
    private final List<String> failedTrackers = new ArrayList<>();
    private @Nullable Thread executionThread;

    private RunManager(final ApplicationDatabase applicationDatabase) {
        this.applicationDatabase = applicationDatabase;
    }

    /**
     * Creates a {@link RunManager}.
     *
     * @param applicationDatabase the {@link ApplicationDatabase} from which trackers and settings are loaded for each execution
     * @return the {@link RunManager}
     */
    public static RunManager create(final ApplicationDatabase applicationDatabase) {
        return new RunManager(applicationDatabase);
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
        // Only apply settings that can be changed at runtime, ignoring any saved for an option that has since become environment-only
        final Map<String, String> overrides = applicationDatabase.findAllSettings()
            .entrySet()
            .stream()
            .filter(setting -> {
                final ConfigurationOption option = ConfigurationOption.find(setting.getKey());
                return option != null && option.editableAtRuntime();
            })
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        final ApplicationConfiguration applicationConfiguration = Configuration.reload(overrides);
        Configurator.setRootLevel(Level.toLevel(applicationConfiguration.logLevel(), Level.INFO));
        return applicationConfiguration;
    }

    /**
     * Starts a new execution in the background, unless one is already running.
     *
     * @return {@code true} if an execution was started, {@code false} if one is already running
     */
    public boolean start() {
        lock.lock();
        try {
            if (state == RunStatus.State.RUNNING || state == RunStatus.State.CANCELLING) {
                return false;
            }

            state = RunStatus.State.RUNNING;
            startedAt = System.currentTimeMillis();
            finishedAt = 0L;
            totalTrackers = 0;
            successfulTrackers.clear();
            failedTrackers.clear();
            ExecutionCancellation.reset();
            executionThread = Thread.ofPlatform()
                .name("web-execution")
                .start(this::execute);
        } finally {
            lock.unlock();
        }
        return true;
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

    private void execute() {
        ExitState exitState = ExitState.FAILURE;
        try {
            exitState = executeTrackers();
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

    private ExitState executeTrackers() throws IOException, SQLException {
        LOGGER.info("Starting execution from web UI");
        reloadConfiguration(applicationDatabase);

        final List<TrackerCredential> enabledTrackers = applicationDatabase.findAllCredentials()
            .stream()
            .filter(StoredTrackerCredential::enabled)
            .map(StoredTrackerCredential::toTrackerCredential)
            .toList();

        final Map<TrackerType, Set<TrackerCredential>> trackersByType = TrackerRetriever.groupByType(enabledTrackers);
        if (!DisplayValidator.isValid(trackersByType, false)) {
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
        lock.lock();
        try {
            return new RunStatus(state, nullIfUnset(startedAt), nullIfUnset(finishedAt), totalTrackers, List.copyOf(successfulTrackers),
                List.copyOf(failedTrackers));
        } finally {
            lock.unlock();
        }
    }

    private static @Nullable Long nullIfUnset(final long epochMillis) {
        return epochMillis == 0L ? null : epochMillis;
    }

    @Override
    public void executionStarted(final int numberOfTrackers) {
        lock.lock();
        try {
            totalTrackers = numberOfTrackers;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void trackerCompleted(final TrackerType trackerType, final String trackerName, final boolean wasSuccessful) {
        lock.lock();
        try {
            if (wasSuccessful) {
                successfulTrackers.add(trackerName);
            } else {
                failedTrackers.add(trackerName);
            }
        } finally {
            lock.unlock();
        }
    }
}
