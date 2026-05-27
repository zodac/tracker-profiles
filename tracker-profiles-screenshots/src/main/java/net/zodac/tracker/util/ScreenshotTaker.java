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
import javax.imageio.ImageIO;
import net.zodac.tracker.framework.config.ApplicationConfiguration;
import net.zodac.tracker.framework.config.Configuration;
import net.zodac.tracker.framework.redactor.RedactionType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.remote.RemoteWebDriver;
import ru.yandex.qatools.ashot.AShot;
import ru.yandex.qatools.ashot.shooting.ShootingStrategies;
import ru.yandex.qatools.ashot.shooting.ShootingStrategy;

/**
 * Utility class used to take a screenshot of a website.
 */
public final class ScreenshotTaker {

    private static final Logger LOGGER = LogManager.getLogger();
    private static final ApplicationConfiguration CONFIG = Configuration.get();
    private static final Duration TIME_BETWEEN_SCROLLS = Duration.ofMillis(500L);
    private static final ExecutorService WRITE_EXECUTOR = Executors.newFixedThreadPool(CONFIG.numberOfParallelThreads());
    private static final Duration SHUTDOWN_DRAIN_TIMEOUT = Duration.ofSeconds(5L);

    private ScreenshotTaker() {

    }

    /**
     * Checks how many screenshots already exist for the given base name in the given directory. The base name is either the tracker name alone (for
     * {@link RedactionType#NONE}) or the tracker name with the redaction type appended (e.g.
     * {@code trackerName_Text}).
     *
     * <p>
     * A file is counted if it matches exactly {@code baseName.png} or {@code baseName_N.png} where {@code N} is a positive integer. This avoids
     * counting files for other redaction types that share the same tracker name prefix.
     *
     * @param baseName  the base file name to match against (tracker name, with optional redaction type suffix)
     * @param directory the directory in which to check for existing screenshots
     * @return the number of screenshots for the base name that already exist in the given directory
     */
    public static int howManyScreenshotsAlreadyExist(final String baseName, final Path directory) {
        final File outputDir = directory.toAbsolutePath().toFile();
        final File[] matchingFiles = outputDir.listFiles((_, name) -> {
            if (!name.endsWith(".png")) {
                return false;
            }
            final String nameWithoutExtension = name.substring(0, name.length() - ".png".length());
            if (nameWithoutExtension.equals(baseName)) {
                return true;
            }
            if (!nameWithoutExtension.startsWith(baseName + "_")) {
                return false;
            }
            final String indexSuffix = nameWithoutExtension.substring(baseName.length() + 1);
            return !indexSuffix.isEmpty() && indexSuffix.chars().allMatch(Character::isDigit);
        });

        return matchingFiles == null ? 0 : matchingFiles.length;
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
     * @param index                  how many screenshots already exist for this base name
     * @return a {@link Future} that resolves to the saved screenshot {@link File} once PNG encoding is complete
     */
    public static Future<File> takeScreenshot(final RemoteWebDriver driver, final Path outputDirectory, final String baseName,
                                              final boolean scrollDuringScreenshot, final int index) {
        final BufferedImage screenshotImage = takeScreenshotOfEntirePage(driver, scrollDuringScreenshot);
        final File screenshot = createOutputFileHandle(outputDirectory.toAbsolutePath(), baseName, index);

        return WRITE_EXECUTOR.submit(() -> {
            ImageIO.write(screenshotImage, "PNG", screenshot);
            return screenshot;
        });
    }

    /**
     * Shuts down the bounded PNG write executor and waits up to {@link #SHUTDOWN_DRAIN_TIMEOUT} seconds for queued writes to drain. If the
     * drain does not complete in time, in-flight writes are cancelled via {@link ExecutorService#shutdownNow()}. Safe to call multiple times.
     */
    public static void shutdown() {
        WRITE_EXECUTOR.shutdown();
        try {
            if (!WRITE_EXECUTOR.awaitTermination(SHUTDOWN_DRAIN_TIMEOUT.toSeconds(), TimeUnit.SECONDS)) {
                LOGGER.warn("PNG write executor did not drain within {}, cancelling in-flight writes", SHUTDOWN_DRAIN_TIMEOUT);
                WRITE_EXECUTOR.shutdownNow();
            }
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.debug("Interrupted while draining PNG write executor", e);
            LOGGER.warn("Interrupted while draining PNG write executor: {}", e.getMessage());
            WRITE_EXECUTOR.shutdownNow();
        }
    }

    private static File createOutputFileHandle(final Path outputDirectory, final String baseName, final int index) {
        if (index == 0) {
            return new File(outputDirectory + File.separator + baseName + ".png");
        }

        return new File(outputDirectory + File.separator + baseName + "_" + index + ".png");
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
