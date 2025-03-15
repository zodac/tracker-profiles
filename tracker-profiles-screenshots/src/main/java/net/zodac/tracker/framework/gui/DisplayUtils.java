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

package net.zodac.tracker.framework.gui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.GridLayout;
import java.awt.Toolkit;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import net.zodac.tracker.framework.exception.CancelledInputException;
import net.zodac.tracker.framework.exception.NoUserInputException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Utility class used to display pop-up windows and confirmation boxes to the user.
 */
public final class DisplayUtils {

    /**
     * The {@link Duration} the program will wait for a user to enter an input.
     */
    public static final Duration INPUT_WAIT_DURATION = Duration.ofMinutes(5L);

    private static final Duration COUNTDOWN_INCREMENT_INTERVAL = Duration.ofSeconds(1L);
    private static final long SECONDS_IN_MINUTE = Duration.ofMinutes(1L).toSeconds();
    private static final Logger LOGGER = LogManager.getLogger();

    // UI element constants
    private static final String TITLE_SUFFIX = " Manual Input";
    private static final String LABEL_SUFFIX = ", then click 'Continue'";
    private static final String BUTTON_CONTINUE_TEXT = "Continue"; // TODO: Add another button to cancel
    private static final String REMAINING_TIME_PREFIX = "Time remaining: ";
    private static final int DIALOG_BOX_HEIGHT = 200;
    private static final int DIALOG_BOX_WIDTH = 500;

    private DisplayUtils() {

    }

    /**
     * Creates a pop-up on the screen for the user to click to confirm a user input has been provided to the loaded tracker.
     *
     * @param titlePrefix the title for the pop-up
     * @param labelPrefix the text for the pop-up
     */
    public static void userInputConfirmation(final String titlePrefix, final String labelPrefix) {
        setStyleToSystemTheme();

        final boolean[] userInputs = {false};
        final String countdownText = getFormattedCountdownText(INPUT_WAIT_DURATION.getSeconds());
        final JLabel countdownLabel = new JLabel(countdownText, SwingConstants.CENTER);
        final JDialog dialog = createDialog(titlePrefix, labelPrefix, countdownLabel, userInputs);

        // Timer for countdown update
        final Timer timer = createTimer(countdownLabel);
        timer.setInitialDelay(0);
        timer.start();

        showDialogWithTimer(dialog, timer, userInputs);
    }

    private static JDialog createDialog(final String titlePrefix, final String labelPrefix, final JLabel countdownLabel, final boolean[] userInputs) {
        // Create a dialog box with a 'Continue' button and countdown label
        final JDialog dialog = new JDialog((Frame) null, titlePrefix + TITLE_SUFFIX, true);
        dialog.setLayout(new BorderLayout());
        dialog.setAlwaysOnTop(true);  // Ensure the dialog remains on top of all windows when interacting with browser

        final JPanel panel = new JPanel(new GridLayout(2, 1));
        panel.add(new JLabel(labelPrefix + LABEL_SUFFIX, SwingConstants.CENTER));
        panel.add(countdownLabel);

        final JButton continueButton = new JButton(BUTTON_CONTINUE_TEXT);
        continueButton.addActionListener(_ -> {
            dialog.dispose();
            userInputs[0] = true;
        });

        dialog.add(panel, BorderLayout.CENTER);
        dialog.add(continueButton, BorderLayout.PAGE_END);
        dialog.setSize(DIALOG_BOX_WIDTH, DIALOG_BOX_HEIGHT);
        setDialogPosition(dialog);
        return dialog;
    }

    private static Timer createTimer(final JLabel countdownLabel) {
        return new Timer(((Long) COUNTDOWN_INCREMENT_INTERVAL.toMillis()).intValue(), _ -> {
            final long remainingTime = parseRemainingSeconds(countdownLabel.getText());
            final long updatedTime = Math.max(0, remainingTime - COUNTDOWN_INCREMENT_INTERVAL.getSeconds());
            countdownLabel.setText(getFormattedCountdownText(updatedTime));
        });
    }

    private static long parseRemainingSeconds(final String text) {
        final String timeText = text.replace(REMAINING_TIME_PREFIX, "").trim();

        if (timeText.contains(":")) {
            // Parse mm:ss format
            final String[] parts = timeText.split(":");
            final long minutes = Long.parseLong(parts[0]);
            final long seconds = Long.parseLong(parts[1]);
            return minutes * SECONDS_IN_MINUTE + seconds;
        } else {
            // Parse "ss seconds" format
            return Long.parseLong(timeText.replace(" seconds", ""));
        }
    }

    private static String getFormattedCountdownText(final long remainingTime) {
        if (remainingTime < SECONDS_IN_MINUTE) {
            return String.format("%s%d seconds", REMAINING_TIME_PREFIX, remainingTime);
        }

        final long minutes = remainingTime / SECONDS_IN_MINUTE;
        final long seconds = remainingTime % SECONDS_IN_MINUTE;
        return String.format("%s%02d:%02d", REMAINING_TIME_PREFIX, minutes, seconds);
    }

    private static void showDialogWithTimer(final JDialog dialog, final Timer timer, final boolean[] userInputs) {
        try (final ExecutorService executor = Executors.newSingleThreadExecutor()) {
            final Future<?> future = executor.submit(() -> dialog.setVisible(true));
            executor.shutdown();

            future.get(INPUT_WAIT_DURATION.getSeconds(), TimeUnit.SECONDS);
            if (!userInputs[0]) { // Dialog has been cancelled since no input was set
                throw new CancelledInputException();
            }
        } catch (final TimeoutException | InterruptedException | ExecutionException e) {
            Thread.currentThread().interrupt();
            throw new NoUserInputException(INPUT_WAIT_DURATION, e);
        } finally {
            dialog.dispose();
            timer.stop();
        }
    }

    private static void setDialogPosition(final JDialog dialog) {
        // Get the screen size and adjust the dialog's position
        final Toolkit toolkit = Toolkit.getDefaultToolkit();
        final Dimension screenSize = toolkit.getScreenSize();
        final int screenWidth = screenSize.width;
        final int dialogWidth = dialog.getWidth();

        final int horizontalOffset = (screenWidth / 4) + (screenWidth / 8);
        final int xPosition = (screenWidth - dialogWidth) / 2 - horizontalOffset;
        final int yPosition = (screenSize.height - dialog.getHeight()) / 2; // Center vertically
        dialog.setLocation(xPosition, yPosition);
    }

    private static void setStyleToSystemTheme() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (final ClassNotFoundException | IllegalAccessException | InstantiationException | UnsupportedLookAndFeelException e) {
            LOGGER.debug("Unexpected error setting UI style", e);
        }
    }
}
