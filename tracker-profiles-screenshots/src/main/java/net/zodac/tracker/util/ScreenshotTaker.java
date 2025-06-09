/*
 * BSD Zero Clause License
 *
 * Copyright (c) 2024-2025 zodac.net
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
import java.time.Duration;
import javax.imageio.ImageIO;
import net.zodac.tracker.framework.ApplicationConfiguration;
import net.zodac.tracker.framework.Configuration;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.remote.RemoteWebDriver;
import ru.yandex.qatools.ashot.AShot;
import ru.yandex.qatools.ashot.shooting.ShootingStrategies;

/**
 * Utility class used to take a screenshot of a website.
 */
public final class ScreenshotTaker {

    private static final ApplicationConfiguration CONFIG = Configuration.get();
    private static final Duration TIME_BETWEEN_SCROLLS = Duration.ofMillis(500L);

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
     * @param driver      the {@link RemoteWebDriver} with the loaded web page
     * @param trackerName the name of the tracker having a screenshot taken (used as the file name)
     * @return the {@link File} instance of the saved screenshot
     * @throws IOException thrown if an error occurs saving the screenshot to the file system
     * @see FileOpener#open(File)
     * @see ScriptExecutor#scrollToTheTop()
     */
    public static File takeScreenshot(final RemoteWebDriver driver, final String trackerName) throws IOException {
        final ScriptExecutor scriptExecutor = new ScriptExecutor(driver);
        final BufferedImage screenshotImage = takeScreenshotOfEntirePage(driver, scriptExecutor);
        final File screenshot = new File(CONFIG.outputDirectory().toAbsolutePath() + File.separator + trackerName + ".png");
        ImageIO.write(screenshotImage, "PNG", screenshot);
        scriptExecutor.scrollToTheTop();
        return screenshot;
    }

    private static BufferedImage takeScreenshotOfEntirePage(final WebDriver driver, final ScriptExecutor scriptExecutor) {
        scriptExecutor.disableScrolling();
        final BufferedImage screenshot = new AShot()
            .shootingStrategy(ShootingStrategies.viewportPasting(((Long) TIME_BETWEEN_SCROLLS.toMillis()).intValue()))
            .takeScreenshot(driver)
            .getImage();

        scriptExecutor.enableScrolling("body");
        return screenshot;
    }
}
