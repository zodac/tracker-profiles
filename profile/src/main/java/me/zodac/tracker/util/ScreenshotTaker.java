package me.zodac.tracker.util;

import java.awt.GridLayout;
import java.io.File;
import java.io.IOException;
import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import org.apache.commons.io.FileUtils;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.remote.RemoteWebDriver;

public final class ScreenshotTaker {

    private static final Dimension DEFAULT_WINDOW_SIZE = new Dimension(1680, 1050);

    private ScreenshotTaker() {

    }

    public static File takeScreenshot(
        final RemoteWebDriver driver,
        final String trackerName,
        final String outputDirectoryPath,
        final boolean previewScreenshot
    ) {
        driver.manage().window().setSize(DEFAULT_WINDOW_SIZE);
        final File rawScreenshot = driver.getScreenshotAs(OutputType.FILE);
        final File screenshot = new File(outputDirectoryPath + File.separator + trackerName + ".png");

        try {
            FileUtils.copyFile(rawScreenshot, screenshot);

            if (previewScreenshot) {
                showImage(screenshot, trackerName);
            }

            return screenshot;
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
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
