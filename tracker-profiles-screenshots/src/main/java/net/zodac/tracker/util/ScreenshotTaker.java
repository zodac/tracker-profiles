/*
 * BSD Zero Clause License
 *
 * Copyright (c) 2021-2025 zodac.net
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

import java.awt.GridLayout;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import net.zodac.tracker.framework.Configuration;
import net.zodac.tracker.framework.ConfigurationProperties;
import org.openqa.selenium.WebDriver;
import ru.yandex.qatools.ashot.AShot;
import ru.yandex.qatools.ashot.shooting.ShootingStrategies;

/**
 * Utility class used to take a screenshot of a website.
 */
public final class ScreenshotTaker {

    private static final ConfigurationProperties CONFIG = Configuration.get();

    private ScreenshotTaker() {

    }

    /**
     * Takes a screenshot of the current web page loaded by the {@link WebDriver}. The browser viewport is then saved as a {@code .png} file in the
     * provided {@code outputDirectory}. The file name will be {@code trackerName.png}.
     *
     * @param driver      the {@link WebDriver} with the loaded web page
     * @param trackerName the name of the tracker having a screenshot taken (used as the file name)
     * @return the {@link File} instance of the saved screenshot
     * @throws IOException thrown if an error occurs saving the screenshot to the file system
     */
    public static File takeScreenshot(final WebDriver driver, final String trackerName) throws IOException {
        final BufferedImage screenshotImage = takeScreenshotOfEntirePage(driver);
        final File screenshot = new File(CONFIG.outputDirectory().toAbsolutePath() + File.separator + trackerName + ".png");
        ImageIO.write(screenshotImage, "PNG", screenshot);

        if (CONFIG.previewTrackerScreenshot()) {
            showImage(screenshot, trackerName);
        }

        return screenshot;
    }

    private static BufferedImage takeScreenshotOfEntirePage(final WebDriver driver) {
        return new AShot()
            .shootingStrategy(ShootingStrategies.viewportPasting(100))
            .takeScreenshot(driver)
            .getImage();
    }

    private static void showImage(final File screenshot, final String trackerName) {
        SwingUtilities.invokeLater(() -> {
            final JFrame jFrame = new JFrame();
            jFrame.setTitle(trackerName);
            jFrame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
            jFrame.getContentPane().setLayout(new GridLayout(1, 1));
            jFrame.getContentPane().add(new JLabel(new ImageIcon(screenshot.getAbsolutePath())));
            jFrame.pack();
            jFrame.setLocationRelativeTo(null);
            jFrame.setVisible(true);
        });
    }
}
