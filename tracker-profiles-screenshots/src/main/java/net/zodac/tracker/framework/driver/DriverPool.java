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
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import net.zodac.tracker.framework.TrackerType;
import net.zodac.tracker.framework.driver.extension.Extension;
import net.zodac.tracker.util.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.remote.RemoteWebDriver;

/**
 * Singleton pool that manages {@link RemoteWebDriver} instances for tracker execution.
 *
 * <p>
 * Trackers that do not require browser extensions share a pool of drivers per {@link TrackerType}. Trackers that implement
 * {@link net.zodac.tracker.handler.definition.UsesExtensions} always receive a fresh driver on each call to {@link #acquire(TrackerType, List)}.
 *
 * <p>
 * Call {@link #initialise(TrackerType, int, int)} once per {@link TrackerType} before execution of that type begins to pre-create its pooled drivers.
 * Callers then acquire a driver via {@link #acquire(TrackerType, List)}, use it, and return it via {@link #release(RemoteWebDriver)}.
 *
 * <p>
 * Call {@link #shutdown()} once all work has completed.
 */
public final class DriverPool {

    private static final Logger LOGGER = LogManager.getLogger();
    private static final String DEFAULT_BROWSER_PAGE = "about:blank";

    private final Lock lock = new ReentrantLock();
    private final AtomicInteger remainingTasks = new AtomicInteger(0);
    private final Map<TrackerType, BlockingDeque<RemoteWebDriver>> pool = new EnumMap<>(TrackerType.class);
    private final Set<RemoteWebDriver> allPooledDrivers = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Map<RemoteWebDriver, TrackerType> driverTypeMap = new IdentityHashMap<>();

    private DriverPool() {

    }

    private static final class InstanceHolder {
        private static final DriverPool INSTANCE = new DriverPool();
    }

    private static DriverPool get() {
        return InstanceHolder.INSTANCE;
    }

    /**
     * Pre-creates pooled {@link RemoteWebDriver} instances for the provided {@link TrackerType}.
     *
     * <p>
     * {@code numberOfParallelThreads} drivers are created and added to the pool. Each driver is registered so that {@link #release(RemoteWebDriver)}
     * can return it correctly.
     *
     * <p>
     * This method must be called once per {@link TrackerType}, before any call to {@link #acquire(TrackerType, List)} for that type.
     *
     * @param trackerType             the {@link TrackerType} to initialise
     * @param numberOfParallelThreads the number of pooled drivers to create
     * @param numberOfTrackers        the total number of trackers for this type
     */
    public static void initialise(final TrackerType trackerType, final int numberOfParallelThreads, final int numberOfTrackers) {
        final DriverPool instance = get();
        final int count = (trackerType == TrackerType.HEADLESS) ? numberOfParallelThreads : 1;
        LOGGER.debug("Initializing {} pooled {} driver{}", count, trackerType.formattedName(), StringUtils.pluralise(count));
        try {
            BrowserDirectories.createAll();
        } catch (final IOException e) {
            throw new IllegalStateException("Unable to create browser directories: %s".formatted(e.getMessage()), e);
        }
        JavaWebDriverFactory.sweepStaleUserDataDirs();

        final List<RemoteWebDriver> drivers = new ArrayList<>(count);
        try (final ExecutorService executor = Executors.newFixedThreadPool(count)) {
            final List<Future<RemoteWebDriver>> futures =
                executor.invokeAll(Collections.nCopies(count, () -> JavaWebDriverFactory.createDriver(trackerType, List.of())));
            for (final Future<RemoteWebDriver> future : futures) {
                drivers.add(future.get());
            }
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while initialising pooled %s drivers".formatted(trackerType.formattedName()), e);
        } catch (final ExecutionException e) {
            throw new IllegalStateException("Failed to create pooled %s driver".formatted(trackerType.formattedName()), e);
        }

        final BlockingDeque<RemoteWebDriver> deque = new LinkedBlockingDeque<>();
        for (final RemoteWebDriver driver : drivers) {
            deque.addLast(driver);
            instance.allPooledDrivers.add(driver);
            instance.driverTypeMap.put(driver, trackerType);
        }
        instance.pool.put(trackerType, deque);
        instance.remainingTasks.set(numberOfTrackers);
    }

    /**
     * Acquires a {@link RemoteWebDriver} for a tracker.
     *
     * <p>
     * If {@code extensions} is non-empty, a fresh driver is created with those {@link Extension}s installed at browser startup, then each
     * {@link Extension#configure(RemoteWebDriver)} is called. This driver is not pooled and will be quit on {@link #release(RemoteWebDriver)}.
     *
     * <p>
     * If {@code extensions} is empty, the pooled driver for the given {@link TrackerType} is returned, blocking until one is available. The
     * driver will be returned to the pool on {@link #release(RemoteWebDriver)}.
     *
     * @param trackerType the {@link TrackerType} controlling headless vs. UI execution
     * @param extensions  the {@link Extension}s to install and configure; an empty list requests a pooled driver
     * @return the {@link RemoteWebDriver}
     */
    public static RemoteWebDriver acquire(final TrackerType trackerType, final List<Extension> extensions) {
        if (!extensions.isEmpty()) {
            LOGGER.trace("Creating fresh driver for extension-based {} tracker", trackerType);
            final RemoteWebDriver driver = JavaWebDriverFactory.createDriver(trackerType, extensions);
            for (final Extension extension : extensions) {
                LOGGER.trace("Configuring extension {}", extension.getClass().getSimpleName());
                extension.configure(driver);
            }
            return driver;
        }

        final DriverPool instance = get();
        final BlockingDeque<RemoteWebDriver> deque = instance.pool.get(trackerType);
        if (deque != null) {
            LOGGER.trace("Acquiring pooled driver for type {}", trackerType);
            try {
                return deque.takeFirst();
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting for pooled driver for type %s".formatted(trackerType), e);
            }
        }

        // Fallback: initialize() was not called — create a single driver lazily
        LOGGER.debug("No pool initialized for type {}, creating driver lazily", trackerType);
        final RemoteWebDriver lazyDriver = JavaWebDriverFactory.createDriver(trackerType, List.of());
        instance.lock.lock();
        try {
            instance.allPooledDrivers.add(lazyDriver);
            instance.driverTypeMap.put(lazyDriver, trackerType);
            instance.pool.putIfAbsent(trackerType, new LinkedBlockingDeque<>());
        } finally {
            instance.lock.unlock();
        }
        return lazyDriver;
    }

    /**
     * Releases a {@link RemoteWebDriver} back to the pool.
     *
     * <p>
     * Pooled drivers are navigated to {@link #DEFAULT_BROWSER_PAGE} to clear the current page state without quitting the browser, then
     * returned to the pool for the next caller. Drivers not originally from the pool are simply {@link RemoteWebDriver#quit()}.
     *
     * @param driver the {@link RemoteWebDriver} to release
     */
    public static void release(final RemoteWebDriver driver) {
        final DriverPool instance = get();
        instance.remainingTasks.decrementAndGet();
        final boolean isPooled;
        instance.lock.lock();
        try {
            isPooled = instance.allPooledDrivers.contains(driver);
        } finally {
            instance.lock.unlock();
        }
        if (isPooled) {
            LOGGER.trace("Returning pooled driver, navigating to blank page");
            try {
                driver.navigate().to(DEFAULT_BROWSER_PAGE);
                returnDriverToPool(instance, driver);
            } catch (final Exception e) {
                LOGGER.trace("Unable to navigate pooled driver to blank page", e);
                replaceDeadDriverInPool(instance, driver);
            }
        } else {
            LOGGER.trace("Quitting fresh driver");
            driver.quit();
            JavaWebDriverFactory.deleteUserDataDir(driver);
        }
    }

    private static void replaceDeadDriverInPool(final DriverPool instance, final RemoteWebDriver deadDriver) {
        LOGGER.warn("Pooled driver is dead, replacing with a fresh driver");
        final TrackerType type = removeDeadTracker(instance, deadDriver);

        if (type == null) {
            LOGGER.warn("Dead driver has no associated TrackerType, cannot replace");
            return;
        }

        if (instance.remainingTasks.get() == 0) {
            LOGGER.debug("No remaining tasks, skipping dead driver replacement");
            return;
        }

        final BlockingDeque<RemoteWebDriver> deque = instance.pool.get(type);
        if (deque == null) {
            LOGGER.warn("No pool found for type {}, cannot replace dead driver", type.formattedName());
            return;
        }

        final RemoteWebDriver replacement = JavaWebDriverFactory.createDriver(type, List.of());
        instance.lock.lock();
        try {
            instance.allPooledDrivers.add(replacement);
            instance.driverTypeMap.put(replacement, type);
        } finally {
            instance.lock.unlock();
        }
        deque.addFirst(replacement);
        LOGGER.debug("Replaced dead pooled {} driver with a fresh one", type.formattedName());
    }

    private static TrackerType removeDeadTracker(final DriverPool instance, final RemoteWebDriver deadDriver) {
        final TrackerType type;
        instance.lock.lock();
        try {
            type = instance.driverTypeMap.remove(deadDriver);
            instance.allPooledDrivers.remove(deadDriver);
        } finally {
            instance.lock.unlock();
        }
        return type;
    }

    private static void returnDriverToPool(final DriverPool instance, final RemoteWebDriver driver) {
        final TrackerType type;
        instance.lock.lock();
        try {
            type = instance.driverTypeMap.get(driver);
        } finally {
            instance.lock.unlock();
        }
        if (type == null) {
            return;
        }

        final BlockingDeque<RemoteWebDriver> deque = instance.pool.get(type);
        if (deque != null) {
            deque.addFirst(driver);
        }
    }

    /**
     * Shuts down the pool, quitting all pooled {@link RemoteWebDriver} instances and clearing all tracking state.
     */
    public static void shutdown() {
        final DriverPool instance = get();
        instance.lock.lock();
        try {
            final int numberOfPooledDrivers = instance.allPooledDrivers.size();
            LOGGER.debug("Shutting down driver pool with {} pooled driver{}", numberOfPooledDrivers, StringUtils.pluralise(numberOfPooledDrivers));
            for (final RemoteWebDriver driver : instance.allPooledDrivers) {
                driver.quit();
                JavaWebDriverFactory.deleteUserDataDir(driver);
            }
            instance.pool.clear();
            instance.allPooledDrivers.clear();
            instance.driverTypeMap.clear();
            instance.remainingTasks.set(0);
        } finally {
            instance.lock.unlock();
        }
    }
}
