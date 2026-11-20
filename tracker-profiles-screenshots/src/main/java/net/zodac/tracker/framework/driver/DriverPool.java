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

package net.zodac.tracker.framework.driver;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import net.zodac.tracker.framework.ExecutionCancellation;
import net.zodac.tracker.framework.TrackerType;
import net.zodac.tracker.framework.driver.extension.Extension;
import net.zodac.tracker.framework.exception.ExecutionCancelledException;
import net.zodac.tracker.handler.definition.UsesExtensions;
import net.zodac.tracker.util.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jspecify.annotations.Nullable;
import org.openqa.selenium.remote.RemoteWebDriver;

/**
 * Singleton pool that manages {@link RemoteWebDriver} instances for tracker execution.
 *
 * <p>
 * Trackers of every {@link TrackerType} run in parallel, so the pool holds browsers of each type (headless, or with a UI), and at most
 * {@code capacity} browsers are open at once (set by {@link #initialise(int, List)}). A tracker reuses an idle browser of its type, otherwise a new
 * one is started, closing an idle browser of another type first if the pool is full. As each tracker holds at most one browser, and no more
 * trackers run at once than the capacity, a tracker never waits for a browser.
 *
 * <p>
 * Trackers that implement {@link UsesExtensions} cannot share pooled drivers (extensions must be installed at browser startup), so each call to
 * {@link #acquire(TrackerType, List)} with extensions starts a fresh browser (counted towards the capacity), which is quit on
 * {@link #release(RemoteWebDriver)}.
 *
 * <p>
 * Call {@link #shutdown()} once all work has completed. {@link #shutdown()} is also used to cancel an execution: it quits every browser (pooled and
 * fresh), and once {@link ExecutionCancellation#isCancelled()} is {@code true}, no new browser is created or handed out.
 */
public final class DriverPool {

    private static final Logger LOGGER = LogManager.getLogger();
    private static final String DEFAULT_BROWSER_PAGE = "about:blank";
    private static final Duration TERMINATION_TIMEOUT = Duration.ofSeconds(3L);

    private final Lock lock = new ReentrantLock();
    // Idle pooled drivers of each type, the most recently used first
    private final Map<TrackerType, Deque<RemoteWebDriver>> idleDrivers = new EnumMap<>(TrackerType.class);
    // Every pooled driver (idle or in use), with its type
    private final IdentityHashMap<RemoteWebDriver, TrackerType> pooledDrivers = new IdentityHashMap<>(); //NOPMD: LooseCoupling - Identity Map usage
    // Extension-equipped drivers currently in use, which are not pooled but must still be quit if the execution is cancelled
    private final Set<RemoteWebDriver> freshDrivers = Collections.newSetFromMap(new IdentityHashMap<>());
    private int capacity = Integer.MAX_VALUE;
    private int driversStarting;

    private DriverPool() {

    }

    private static final class InstanceHolder {
        private static final DriverPool INSTANCE = new DriverPool();
    }

    private static DriverPool get() {
        return InstanceHolder.INSTANCE;
    }

    /**
     * Sets the maximum number of open browsers, and pre-creates an idle pooled {@link RemoteWebDriver} for each provided {@link TrackerType}.
     *
     * @param capacity     the maximum number of browsers open at once (the number of trackers executed in parallel)
     * @param driverTypes  the {@link TrackerType} of each driver to pre-create (at most {@code capacity})
     */
    public static void initialise(final int capacity, final List<TrackerType> driverTypes) {
        ExecutionCancellation.throwIfCancelled();
        LOGGER.debug("Initializing {} pooled driver{} (at most {} open at once)", driverTypes.size(), StringUtils.pluralise(driverTypes.size()),
            capacity);
        try {
            BrowserDirectories.createAll();
        } catch (final IOException e) {
            throw new IllegalStateException("Unable to create browser directories: %s".formatted(e.getMessage()), e);
        }
        JavaWebDriverFactory.sweepStaleUserDataDirs();

        final DriverPool instance = get();
        instance.lock.lock();
        try {
            instance.capacity = capacity;
        } finally {
            instance.lock.unlock();
        }

        final List<RemoteWebDriver> drivers = createDrivers(driverTypes);
        instance.lock.lock();
        try {
            for (int i = 0; i < drivers.size(); i++) {
                final RemoteWebDriver driver = drivers.get(i);
                final TrackerType trackerType = driverTypes.get(i);
                instance.pooledDrivers.put(driver, trackerType);
                instance.idleDrivers.computeIfAbsent(trackerType, _ -> new ArrayDeque<>()).addLast(driver);
            }
        } finally {
            instance.lock.unlock();
        }

        // If cancelled while the drivers were starting, the cancellation's shutdown() may have run before they were registered
        if (ExecutionCancellation.isCancelled()) {
            shutdown();
            throw new ExecutionCancelledException();
        }
    }

    // Creates the drivers in parallel. If interrupted (the execution is cancelled), this returns immediately rather than waiting for a creation that
    // may be stuck. Any driver that has been, or is later, created is then quit, so no browser is left running
    private static List<RemoteWebDriver> createDrivers(final List<TrackerType> trackerTypes) {
        final int count = trackerTypes.size();
        if (count == 0) {
            return List.of();
        }
        // Shut down rather than closed: close() waits for every creation, so a cancellation would wait for a stuck browser to start
        final ExecutorService executor = Executors.newFixedThreadPool(count); // NOPMD: CloseResource - see above
        final List<CompletableFuture<RemoteWebDriver>> futures = new ArrayList<>(count);
        for (final TrackerType trackerType : trackerTypes) {
            futures.add(CompletableFuture.supplyAsync(() -> JavaWebDriverFactory.createDriver(trackerType, List.of()), executor));
        }
        executor.shutdown(); // No new tasks; the threads end once their creation completes

        try {
            CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).get();
            return futures.stream().map(CompletableFuture::join).toList();
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            quitWhenCreated(futures);
            ExecutionCancellation.throwIfCancelled();
            throw new IllegalStateException("Interrupted while initialising pooled drivers", e);
        } catch (final ExecutionException e) {
            quitWhenCreated(futures);
            throw new IllegalStateException("Failed to create pooled driver", e);
        }
    }

    // Quits each driver now if already created, or as soon as its creation completes
    private static void quitWhenCreated(final List<CompletableFuture<RemoteWebDriver>> futures) {
        LOGGER.trace("Quitting created {} driver(s) after a failed initialisation", futures.size());
        for (final CompletableFuture<RemoteWebDriver> future : futures) {
            future.thenAccept(DriverPool::quitAndCleanup);
        }
    }

    /**
     * Acquires a {@link RemoteWebDriver} for a tracker.
     *
     * <p>
     * If {@code extensions} is empty, an idle pooled driver of the given {@link TrackerType} is returned if there is one, otherwise a new pooled
     * driver is started. The driver is returned to the pool on {@link #release(RemoteWebDriver)}.
     *
     * <p>
     * If {@code extensions} is non-empty, a fresh driver is started with those {@link Extension}s installed at browser startup, and each
     * {@link Extension#configure(RemoteWebDriver)} is called. The fresh driver is not pooled, and is quit on {@link #release(RemoteWebDriver)}.
     *
     * <p>
     * Before a driver is started, an idle pooled driver (of another {@link TrackerType} if possible) is quit if the pool is full.
     *
     * @param trackerType the {@link TrackerType} controlling headless vs. UI execution
     * @param extensions  the {@link Extension}s to install and configure; an empty list requests a pooled driver
     * @return the {@link RemoteWebDriver}
     */
    public static RemoteWebDriver acquire(final TrackerType trackerType, final List<Extension> extensions) {
        ExecutionCancellation.throwIfCancelled();
        final DriverPool instance = get();
        final boolean pooled = extensions.isEmpty();

        final RemoteWebDriver evicted;
        instance.lock.lock();
        try {
            final Deque<RemoteWebDriver> idle = instance.idleDrivers.get(trackerType);
            if (pooled && idle != null && !idle.isEmpty()) {
                LOGGER.trace("Acquiring idle pooled {} driver", trackerType);
                return idle.removeFirst();
            }

            evicted = instance.removeIdleDriverIfFull(trackerType);
            instance.driversStarting++;
        } finally {
            instance.lock.unlock();
        }

        if (evicted != null) {
            LOGGER.trace("Closing an idle pooled driver to make room for a {} driver", trackerType);
            quitAndCleanup(evicted);
        }

        final RemoteWebDriver driver;
        try {
            driver = startDriver(trackerType, extensions);
        } finally {
            instance.lock.lock();
            try {
                instance.driversStarting--;
            } finally {
                instance.lock.unlock();
            }
        }

        instance.lock.lock();
        try {
            if (pooled) {
                instance.pooledDrivers.put(driver, trackerType);
            } else {
                instance.freshDrivers.add(driver);
            }
        } finally {
            instance.lock.unlock();
        }
        quitIfCancelled(instance, driver);
        return driver;
    }

    private static RemoteWebDriver startDriver(final TrackerType trackerType, final List<Extension> extensions) {
        LOGGER.trace("Starting {} driver{}", trackerType, extensions.isEmpty() ? "" : " with extensions");
        final RemoteWebDriver driver = JavaWebDriverFactory.createDriver(trackerType, extensions);
        try {
            for (final Extension extension : extensions) {
                LOGGER.trace("Configuring extension {}", extension.getClass().getSimpleName());
                extension.configure(driver);
            }
        } catch (final RuntimeException e) {
            LOGGER.trace("Failed to configure extensions, cleaning up fresh driver", e);
            quitAndCleanup(driver);
            throw e;
        }
        return driver;
    }

    // If the pool is full, removes the least recently used idle driver (of another type than the one needed if possible) to be quit, so a new
    // driver can be started. Must be called while holding the lock
    private @Nullable RemoteWebDriver removeIdleDriverIfFull(final TrackerType neededType) {
        if (pooledDrivers.size() + freshDrivers.size() + driversStarting < capacity) {
            return null;
        }

        final List<Map.Entry<TrackerType, Deque<RemoteWebDriver>>> evictionOrder = new ArrayList<>(idleDrivers.entrySet());
        evictionOrder.sort((first, second) -> Boolean.compare(first.getKey() == neededType, second.getKey() == neededType));
        for (final Map.Entry<TrackerType, Deque<RemoteWebDriver>> entry : evictionOrder) {
            final Deque<RemoteWebDriver> idle = entry.getValue();
            if (!idle.isEmpty()) {
                final RemoteWebDriver evicted = idle.removeLast();
                pooledDrivers.remove(evicted);
                return evicted;
            }
        }
        return null;
    }

    // If cancelled while a driver was being created, the cancellation's shutdown() may have run before it was registered, so quit it here
    private static void quitIfCancelled(final DriverPool instance, final RemoteWebDriver driver) {
        if (!ExecutionCancellation.isCancelled()) {
            return;
        }

        removeFromTracking(instance, driver);
        quitAndCleanup(driver);
        throw new ExecutionCancelledException();
    }

    /**
     * Releases a {@link RemoteWebDriver} back to the pool.
     *
     * <p>
     * Pooled drivers are navigated to {@link #DEFAULT_BROWSER_PAGE} to clear the current page state without quitting the browser, then
     * returned to the pool for the next tracker of the same type. A pooled driver that no longer responds is quit (a new one is started when
     * needed). Drivers not from the pool are simply {@link RemoteWebDriver#quit()}.
     *
     * @param driver the {@link RemoteWebDriver} to release
     */
    public static void release(final RemoteWebDriver driver) {
        final DriverPool instance = get();
        final TrackerType trackerType;
        instance.lock.lock();
        try {
            trackerType = instance.pooledDrivers.get(driver);
            instance.freshDrivers.remove(driver);
        } finally {
            instance.lock.unlock();
        }

        if (trackerType == null) {
            LOGGER.trace("Quitting fresh driver");
            quitAndCleanup(driver);
            return;
        }

        LOGGER.trace("Returning pooled driver, navigating to blank page");
        try {
            driver.navigate().to(DEFAULT_BROWSER_PAGE);
        } catch (final Exception e) {
            LOGGER.trace("Unable to navigate pooled driver to blank page", e);
            removeFromTracking(instance, driver);
            quitAndCleanup(driver);
            // Every browser is closed when an execution is cancelled, so a dead driver is expected
            if (!ExecutionCancellation.isCancelled()) {
                LOGGER.warn("Pooled driver is dead, closing it (a new one is started when needed)");
            }
            return;
        }

        instance.lock.lock();
        try {
            // Not returned if the pool was shut down in the meantime
            if (instance.pooledDrivers.containsKey(driver)) {
                instance.idleDrivers.computeIfAbsent(trackerType, _ -> new ArrayDeque<>()).addFirst(driver);
                return;
            }
        } finally {
            instance.lock.unlock();
        }
        quitAndCleanup(driver);
    }

    private static void removeFromTracking(final DriverPool instance, final RemoteWebDriver driver) {
        instance.lock.lock();
        try {
            final TrackerType trackerType = instance.pooledDrivers.remove(driver);
            final Deque<RemoteWebDriver> idle = trackerType == null ? null : instance.idleDrivers.get(trackerType);
            if (idle != null) {
                idle.remove(driver);
            }
            instance.freshDrivers.remove(driver);
        } finally {
            instance.lock.unlock();
        }
    }

    private static void quitAndCleanup(final RemoteWebDriver driver) {
        try {
            driver.quit();
        } catch (final Exception e) {
            LOGGER.trace("Error quitting driver during cleanup", e);
        }
        JavaWebDriverFactory.deleteUserDataDir(driver);
    }

    /**
     * Immediately stops every browser started by this application, then {@link #shutdown() shuts down} the pool. Used to cancel an execution.
     *
     * <p>
     * Quitting through Selenium only reaches drivers that have finished starting, and waits on each browser. Instead, every browser process
     * started by this JVM ({@code chromedriver}, the browsers it launched, and {@code selenium-manager}) is terminated directly, including
     * browsers that are still starting. Processes still running after {@link #TERMINATION_TIMEOUT} are killed forcibly. In-progress Selenium
     * calls then fail quickly, and the subsequent {@link #shutdown()} has nothing left to wait on.
     */
    public static void terminateAll() {
        final List<ProcessHandle> browserProcesses = ProcessHandle.current()
            .descendants()
            .filter(DriverPool::isBrowserProcess)
            .toList();
        LOGGER.debug("Terminating {} browser process{}", browserProcesses.size(), browserProcesses.size() == 1 ? "" : "es");
        browserProcesses.forEach(ProcessHandle::destroy);

        try {
            CompletableFuture.allOf(browserProcesses.stream().map(ProcessHandle::onExit).toArray(CompletableFuture[]::new))
                .get(TERMINATION_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (final TimeoutException | ExecutionException e) {
            LOGGER.trace("Not all browser processes terminated, killing forcibly", e);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.trace("Interrupted while waiting for browser processes to terminate, killing forcibly", e);
        }
        browserProcesses.stream()
            .filter(ProcessHandle::isAlive)
            .forEach(ProcessHandle::destroyForcibly);

        shutdown();
    }

    private static boolean isBrowserProcess(final ProcessHandle processHandle) {
        final String command = processHandle.info().command().orElse("").toLowerCase(Locale.ROOT);
        // Python runs the zendriver Cloudflare check (see CloudflareSolver), attached to a browser
        return command.contains("chrom") || command.contains("selenium-manager") || command.contains("python");
    }

    /**
     * Shuts down the pool, quitting all pooled and fresh (extension-equipped) {@link RemoteWebDriver} instances and clearing all tracking state.
     * Safe to call multiple times, and from any thread (such as when cancelling an execution).
     */
    public static void shutdown() {
        final DriverPool instance = get();
        instance.lock.lock();
        try {
            final int numberOfPooledDrivers = instance.pooledDrivers.size();
            LOGGER.debug("Shutting down driver pool with {} pooled driver{}", numberOfPooledDrivers, StringUtils.pluralise(numberOfPooledDrivers));
            for (final RemoteWebDriver driver : instance.pooledDrivers.keySet()) {
                quitAndCleanup(driver);
            }
            for (final RemoteWebDriver driver : instance.freshDrivers) {
                quitAndCleanup(driver);
            }
            instance.idleDrivers.clear();
            instance.pooledDrivers.clear();
            instance.freshDrivers.clear();
            instance.capacity = Integer.MAX_VALUE;
        } finally {
            instance.lock.unlock();
        }
    }
}
