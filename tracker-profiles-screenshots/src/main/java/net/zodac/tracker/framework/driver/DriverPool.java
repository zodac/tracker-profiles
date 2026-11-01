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
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import net.zodac.tracker.framework.TrackerType;
import net.zodac.tracker.framework.driver.extension.Extension;
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
 * Trackers that do not require browser extensions share a pool of drivers per {@link TrackerType}. Trackers that implement
 * {@link UsesExtensions} cannot share pooled drivers (extensions must be installed at browser startup), so each call to
 * {@link #acquire(TrackerType, List)} borrows a slot from the pool for a fresh extension-equipped driver, refilling it on {@link #release}.
 *
 * <p>
 * Call {@link #initialise(TrackerType, int)} once per {@link TrackerType} before execution of that type begins to pre-create its pooled drivers.
 * Callers then acquire a driver via {@link #acquire(TrackerType, List)}, use it, and return it via {@link #release(RemoteWebDriver)}.
 *
 * <p>
 * Call {@link #shutdown()} once all work has completed.
 */
public final class DriverPool {

    private static final Logger LOGGER = LogManager.getLogger();
    private static final String DEFAULT_BROWSER_PAGE = "about:blank";

    private final Lock lock = new ReentrantLock();
    private final Map<TrackerType, BlockingDeque<RemoteWebDriver>> pool = new EnumMap<>(TrackerType.class);
    private final Set<RemoteWebDriver> allPooledDrivers = Collections.newSetFromMap(new IdentityHashMap<>());
    private final IdentityHashMap<RemoteWebDriver, TrackerType> driverTypeMap = new IdentityHashMap<>(); //NOPMD: LooseCoupling - Identity Map usage
    private final IdentityHashMap<RemoteWebDriver, TrackerType> borrowedSlots = new IdentityHashMap<>(); //NOPMD: LooseCoupling - Identity Map usage

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
     */
    public static void initialise(final TrackerType trackerType, final int numberOfParallelThreads) {
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

        final DriverPool instance = get();
        final BlockingDeque<RemoteWebDriver> deque = new LinkedBlockingDeque<>();
        for (final RemoteWebDriver driver : drivers) {
            deque.addLast(driver);
            instance.allPooledDrivers.add(driver);
            instance.driverTypeMap.put(driver, trackerType);
        }
        instance.pool.put(trackerType, deque);
    }

    /**
     * Acquires a {@link RemoteWebDriver} for a tracker.
     *
     * <p>
     * If {@code extensions} is non-empty, a slot is borrowed from the pool for the given {@link TrackerType} (evicting and quitting one pooled
     * driver, and blocking until one is free) so the number of concurrent browsers stays bounded by the pool size. A fresh driver is then created
     * with those {@link Extension}s installed at browser startup, and each {@link Extension#configure(RemoteWebDriver)} is called. The fresh driver
     * is not pooled; on {@link #release(RemoteWebDriver)} it is quit and a replacement pooled driver refills the borrowed slot.
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
        final DriverPool instance = get();
        if (!extensions.isEmpty()) {
            return acquireExtensionDriver(instance, trackerType, extensions);
        }

        final BlockingDeque<RemoteWebDriver> deque;
        instance.lock.lock();
        try {
            deque = instance.pool.get(trackerType);
        } finally {
            instance.lock.unlock();
        }
        if (deque != null) {
            LOGGER.trace("Acquiring pooled driver for type {}", trackerType);
            try {
                return deque.takeFirst();
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting for pooled driver for type %s".formatted(trackerType), e);
            }
        }

        // Fallback: initialize() was not called, so we create a single driver lazily
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

    private static RemoteWebDriver acquireExtensionDriver(final DriverPool instance, final TrackerType trackerType,
                                                          final List<Extension> extensions) {
        LOGGER.trace("Creating fresh driver for extension-based {} tracker", trackerType);
        final boolean borrowed = borrowPoolSlot(instance, trackerType);

        final RemoteWebDriver driver;
        try {
            driver = JavaWebDriverFactory.createDriver(trackerType, extensions);
        } catch (final RuntimeException e) {
            LOGGER.trace("Failed to create extension driver", e);
            if (borrowed) {
                addFreshPooledDriver(instance, trackerType);
            }
            throw e;
        }

        try {
            for (final Extension extension : extensions) {
                LOGGER.trace("Configuring extension {}", extension.getClass().getSimpleName());
                extension.configure(driver);
            }
        } catch (final RuntimeException e) {
            LOGGER.trace("Failed to configure extensions, cleaning up fresh driver", e);
            quitAndCleanup(driver);
            if (borrowed) {
                addFreshPooledDriver(instance, trackerType);
            }
            throw e;
        }

        if (borrowed) {
            instance.lock.lock();
            try {
                instance.borrowedSlots.put(driver, trackerType);
            } finally {
                instance.lock.unlock();
            }
        }
        return driver;
    }

    // Evicts and quits one pooled driver for the given type so an extension-based tracker can run without exceeding the pool size. Blocks until a
    // pooled driver is available. Returns true if a slot was borrowed (and must be replenished on release), false if no pool exists for the type.
    private static boolean borrowPoolSlot(final DriverPool instance, final TrackerType trackerType) {
        final BlockingDeque<RemoteWebDriver> deque;
        instance.lock.lock();
        try {
            deque = instance.pool.get(trackerType);
        } finally {
            instance.lock.unlock();
        }
        if (deque == null) {
            return false;
        }

        final RemoteWebDriver evicted;
        try {
            evicted = deque.takeFirst();
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while borrowing pooled slot for type %s".formatted(trackerType), e);
        }
        removeFromTracking(instance, evicted);
        quitAndCleanup(evicted);
        LOGGER.trace("Evicted a pooled {} driver to make room for an extension-based tracker", trackerType.formattedName());
        return true;
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
            quitAndCleanup(driver);
            replenishBorrowedSlot(instance, driver);
        }
    }

    private static void replenishBorrowedSlot(final DriverPool instance, final RemoteWebDriver driver) {
        final TrackerType borrowedType;
        instance.lock.lock();
        try {
            borrowedType = instance.borrowedSlots.remove(driver);
        } finally {
            instance.lock.unlock();
        }
        if (borrowedType != null) {
            LOGGER.trace("Replenishing borrowed pooled slot for type {}", borrowedType.formattedName());
            addFreshPooledDriver(instance, borrowedType);
        }
    }

    private static void replaceDeadDriverInPool(final DriverPool instance, final RemoteWebDriver deadDriver) {
        LOGGER.warn("Pooled driver is dead, replacing with a fresh driver");
        final TrackerType trackerType = removeFromTracking(instance, deadDriver);
        quitAndCleanup(deadDriver);

        if (trackerType == null) {
            LOGGER.warn("Dead driver has no associated TrackerType, cannot replace");
            return;
        }

        if (addFreshPooledDriver(instance, trackerType)) {
            LOGGER.debug("Replaced dead pooled {} driver with a fresh one", trackerType.formattedName());
        } else {
            LOGGER.warn("No pool found for type {}, cannot replace dead driver", trackerType.formattedName());
        }
    }

    // Creates a fresh pooled driver for the given type, registers it, and returns it to the pool. Returns false if no pool exists for the type.
    private static boolean addFreshPooledDriver(final DriverPool instance, final TrackerType trackerType) {
        final BlockingDeque<RemoteWebDriver> deque;
        instance.lock.lock();
        try {
            deque = instance.pool.get(trackerType);
        } finally {
            instance.lock.unlock();
        }

        if (deque == null) {
            return false;
        }

        final RemoteWebDriver replacement = JavaWebDriverFactory.createDriver(trackerType, List.of());
        instance.lock.lock();
        try {
            instance.allPooledDrivers.add(replacement);
            instance.driverTypeMap.put(replacement, trackerType);
        } finally {
            instance.lock.unlock();
        }
        deque.addFirst(replacement);
        return true;
    }

    @Nullable
    private static TrackerType removeFromTracking(final DriverPool instance, final RemoteWebDriver driver) {
        instance.lock.lock();
        try {
            final TrackerType trackerType = instance.driverTypeMap.remove(driver);
            instance.allPooledDrivers.remove(driver);
            return trackerType;
        } finally {
            instance.lock.unlock();
        }
    }

    private static void returnDriverToPool(final DriverPool instance, final RemoteWebDriver driver) {
        BlockingDeque<RemoteWebDriver> deque = null;
        instance.lock.lock();
        try {
            final TrackerType type = instance.driverTypeMap.get(driver);
            if (type != null) {
                deque = instance.pool.get(type);
            }
        } finally {
            instance.lock.unlock();
        }

        if (deque != null) {
            deque.addFirst(driver);
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
     * Shuts down the pool, quitting all pooled {@link RemoteWebDriver} instances and clearing all tracking state.
     */
    public static void shutdown() {
        final DriverPool instance = get();
        instance.lock.lock();
        try {
            final int numberOfPooledDrivers = instance.allPooledDrivers.size();
            LOGGER.debug("Shutting down driver pool with {} pooled driver{}", numberOfPooledDrivers, StringUtils.pluralise(numberOfPooledDrivers));
            for (final RemoteWebDriver driver : instance.allPooledDrivers) {
                quitAndCleanup(driver);
            }
            instance.pool.clear();
            instance.allPooledDrivers.clear();
            instance.driverTypeMap.clear();
            instance.borrowedSlots.clear();
        } finally {
            instance.lock.unlock();
        }
    }
}
