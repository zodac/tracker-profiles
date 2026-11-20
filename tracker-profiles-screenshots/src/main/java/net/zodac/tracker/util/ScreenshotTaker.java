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
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import javax.imageio.ImageIO;
import net.zodac.tracker.framework.exception.ScreenshotWriteError;
import org.openqa.selenium.remote.RemoteWebDriver;
import ru.yandex.qatools.ashot.AShot;
import ru.yandex.qatools.ashot.shooting.ShootingStrategies;
import ru.yandex.qatools.ashot.shooting.ShootingStrategy;

/**
 * Utility class used to take a screenshot of a website.
 */
public final class ScreenshotTaker {

    private static final Duration TIME_BETWEEN_SCROLLS = Duration.ofMillis(500L);

    private ScreenshotTaker() {

    }

    /**
     * Takes a screenshot of the entire web page loaded by the {@link RemoteWebDriver}, and saves it as {@code baseName.png} in the provided
     * {@code outputDirectory} (replacing any existing file).
     *
     * @param driver                 the {@link RemoteWebDriver} with the loaded web page
     * @param outputDirectory        the directory in which the screenshot should be saved
     * @param baseName               the base file name for the screenshot (tracker name, with optional redaction type suffix)
     * @param scrollDuringScreenshot whether to scroll the profile page during the screenshot
     * @return the saved screenshot {@link File}
     * @throws ScreenshotWriteError thrown if the screenshot cannot be written
     */
    public static File takeScreenshot(final RemoteWebDriver driver, final Path outputDirectory, final String baseName,
        final boolean scrollDuringScreenshot) {
        final BufferedImage screenshotImage = new AShot()
            .shootingStrategy(shootingStrategy(scrollDuringScreenshot))
            .takeScreenshot(driver)
            .getImage();
        final File screenshot = outputDirectory.toAbsolutePath().resolve(baseName + ".png").toFile();

        try {
            ImageIO.write(screenshotImage, "PNG", screenshot);
        } catch (final IOException e) {
            throw new ScreenshotWriteError(baseName, e);
        }
        return screenshot;
    }

    private static ShootingStrategy shootingStrategy(final boolean scrollDuringScreenshot) {
        return scrollDuringScreenshot
            ? ShootingStrategies.viewportPasting(Math.toIntExact(TIME_BETWEEN_SCROLLS.toMillis()))
            : ShootingStrategies.simple();
    }
}
