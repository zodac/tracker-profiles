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

package net.zodac.tracker.util;

import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import javax.imageio.ImageIO;
import net.zodac.tracker.framework.config.Configuration;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jspecify.annotations.Nullable;
import org.openqa.selenium.remote.RemoteWebDriver;
import ru.yandex.qatools.ashot.AShot;
import ru.yandex.qatools.ashot.shooting.ShootingStrategies;
import ru.yandex.qatools.ashot.shooting.ShootingStrategy;

/**
 * Utility class used to take a screenshot of a website.
 */
public final class ScreenshotTaker {

    private static final Logger LOGGER = LogManager.getLogger();
    private static final Duration TIME_BETWEEN_SCROLLS = Duration.ofMillis(500L);
    private static final Lock WRITE_EXECUTOR_LOCK = new ReentrantLock();
    private static final Duration SHUTDOWN_DRAIN_TIMEOUT = Duration.ofSeconds(5L);

    // Created on first use and recreated after each shutdown(), so the web UI can execute multiple runs in one JVM
    private static final AtomicReference<@Nullable ExecutorService> WRITE_EXECUTOR = new AtomicReference<>();

    private ScreenshotTaker() {

    }

    /**
     * Takes a screenshot of the current web page loaded by the {@link RemoteWebDriver}. The browser viewport is then saved as a {@code .png} file in
     * the provided {@code outputDirectory}. The file name will be {@code trackerName.png}.
     *
     * <p>
     * Once the screenshot is saved, the page is scrolled back to the top. This is to ensure that any elements at the top of the page are clickable
     * after scrolling.
     *
     * @param driver                 the {@link RemoteWebDriver} with the loaded web page
     * @param outputDirectory        the directory in which the screenshot should be saved
     * @param baseName               the base file name for the screenshot (tracker name, with optional redaction type suffix)
     * @param scrollDuringScreenshot whether to scroll the profile page during the screenshot
     * @return a {@link Future} that resolves to the saved screenshot {@link File} once PNG encoding is complete
     */
    public static Future<File> takeScreenshot(final RemoteWebDriver driver, final Path outputDirectory, final String baseName,
        final boolean scrollDuringScreenshot) {
        final BufferedImage screenshotImage = takeScreenshotOfEntirePage(driver, scrollDuringScreenshot);
        final File screenshot = outputDirectory.toAbsolutePath().resolve(baseName + ".png").toFile();

        return getOrCreateWriteExecutor().submit(() -> {
            ImageIO.write(screenshotImage, "PNG", screenshot);
            return screenshot;
        });
    }

    /**
     * Shuts down the bounded PNG write executor and waits up to {@link #SHUTDOWN_DRAIN_TIMEOUT} seconds for queued writes to drain. If the
     * drain does not complete in time, in-flight writes are cancelled via {@link ExecutorService#shutdownNow()}. Safe to call multiple times, and a
     * new executor is created for the next screenshot.
     */
    public static void shutdown() {
        final ExecutorService executorToShutdown; // NOPMD: CloseResource - shut down with a drain timeout below, rather than close()
        WRITE_EXECUTOR_LOCK.lock();
        try {
            executorToShutdown = WRITE_EXECUTOR.getAndSet(null);
        } finally {
            WRITE_EXECUTOR_LOCK.unlock();
        }

        if (executorToShutdown == null) {
            return;
        }

        executorToShutdown.shutdown();
        try {
            if (!executorToShutdown.awaitTermination(SHUTDOWN_DRAIN_TIMEOUT.toSeconds(), TimeUnit.SECONDS)) {
                LOGGER.warn("PNG write executor did not drain within {}, cancelling in-flight writes", SHUTDOWN_DRAIN_TIMEOUT);
                executorToShutdown.shutdownNow();
            }
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.debug("Interrupted while draining PNG write executor", e);
            LOGGER.warn("Interrupted while draining PNG write executor: {}", e.getMessage());
            executorToShutdown.shutdownNow();
        }
    }

    private static ExecutorService getOrCreateWriteExecutor() {
        WRITE_EXECUTOR_LOCK.lock();
        try {
            final ExecutorService existingExecutor = WRITE_EXECUTOR.get();
            if (existingExecutor != null) {
                return existingExecutor;
            }

            final ExecutorService newExecutor = Executors.newFixedThreadPool(Configuration.get().numberOfParallelThreads());
            WRITE_EXECUTOR.set(newExecutor);
            return newExecutor;
        } finally {
            WRITE_EXECUTOR_LOCK.unlock();
        }
    }

    private static BufferedImage takeScreenshotOfEntirePage(final RemoteWebDriver driver, final boolean scrollDuringScreenshot) {
        return new AShot()
            .shootingStrategy(shootingStrategy(scrollDuringScreenshot))
            .takeScreenshot(driver)
            .getImage();
    }

    private static ShootingStrategy shootingStrategy(final boolean scrollDuringScreenshot) {
        return scrollDuringScreenshot
            ? ShootingStrategies.viewportPasting(((Long) TIME_BETWEEN_SCROLLS.toMillis()).intValue())
            : ShootingStrategies.simple();
    }
}
