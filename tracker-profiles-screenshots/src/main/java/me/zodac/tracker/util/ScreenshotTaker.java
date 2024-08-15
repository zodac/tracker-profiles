/*
 * BSD Zero Clause License
 *
 * Copyright (c) 2021-2024 zodac.me
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

package me.zodac.tracker.util;

import java.awt.GridLayout;
import java.io.File;
import java.io.IOException;
import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import me.zodac.tracker.framework.Configuration;
import me.zodac.tracker.framework.ConfigurationProperties;
import org.apache.commons.io.FileUtils;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.remote.RemoteWebDriver;

/**
 * Utility class used to take a screenshot of a website.
 */
public final class ScreenshotTaker {

    /**
     * The default zoom level of 100%.
     */
    public static final double DEFAULT_ZOOM_LEVEL = 1.0D;

    private static final Dimension DEFAULT_WINDOW_SIZE = new Dimension(1680, 1050);
    private static final ConfigurationProperties CONFIG = Configuration.get();

    private ScreenshotTaker() {

    }

    /**
     * Takes a screenshot of the current web page loaded by the {@link RemoteWebDriver}. The size of the browser window is set to
     * {@link #DEFAULT_WINDOW_SIZE} and then a zoom is performed to ensure visibility. The browser viewport is then saved as a {@code .png} file in
     * the provided {@code outputDirectory}. The file name will be {@code trackerName.png}.
     *
     * @param driver      the {@link RemoteWebDriver} with the loaded web page
     * @param trackerName the name of the tracker having a screenshot taken (used as the file name)
     * @param zoomLevel   the zoom level required for the tracker's profile page
     * @return the {@link File} instance of the saved screenshot
     * @throws IOException thrown if an error occurs saving the screenshot to the file system
     */
    public static File takeScreenshot(final RemoteWebDriver driver, final String trackerName, final double zoomLevel) throws IOException {
        final File rawScreenshot = resizeViewportAndTakeScreenshot(driver, zoomLevel);
        final File screenshot = new File(CONFIG.outputDirectory().toAbsolutePath() + File.separator + trackerName + ".png");
        FileUtils.copyFile(rawScreenshot, screenshot);

        if (CONFIG.previewTrackerScreenshot()) {
            showImage(screenshot, trackerName);
        }

        return screenshot;
    }

    private static File resizeViewportAndTakeScreenshot(final RemoteWebDriver driver, final double zoomLevel) {
        ScriptExecutor.zoom(driver, zoomLevel);
        driver.manage().window().setSize(DEFAULT_WINDOW_SIZE);

        final File rawScreenshot = driver.getScreenshotAs(OutputType.FILE);
        ScriptExecutor.zoom(driver, 1.0D);
        return rawScreenshot;
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
