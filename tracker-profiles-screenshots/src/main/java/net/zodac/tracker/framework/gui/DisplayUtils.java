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

    private static final Logger LOGGER = LogManager.getLogger();

    // UI element constants
    private static final String BUTTON_CONTINUE_TEXT = "Continue";
    private static final String BUTTON_EXIT_TEXT = "Exit";
    private static final String LABEL_SUFFIX = String.format(", then click '%s' below", BUTTON_CONTINUE_TEXT);
    private static final String TITLE_SUFFIX = " Manual Input";
    private static final int DIALOG_BOX_HEIGHT = 125;
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
        final JDialog dialog = createDialog(titlePrefix, labelPrefix, userInputs);

        showDialog(dialog, userInputs);
    }

    private static JDialog createDialog(final String titlePrefix, final String labelPrefix, final boolean[] userInputs) {
        // Create a dialog box with a 'Continue' button and countdown label
        final JDialog dialog = new JDialog((Frame) null, titlePrefix + TITLE_SUFFIX, true);
        dialog.setLayout(new BorderLayout());
        dialog.setAlwaysOnTop(true);  // Ensure the dialog remains on top of all windows when interacting with browser

        final JPanel panel = new JPanel(new GridLayout(2, 1));
        final String labelText = "<html>" + labelPrefix + LABEL_SUFFIX + "</html>"; // Wrap text as HTML so .pack() can resize dynamically
        panel.add(new JLabel(labelText, SwingConstants.CENTER));

        dialog.add(panel, BorderLayout.CENTER);
        dialog.add(createButtons(userInputs, dialog), BorderLayout.PAGE_END);

        dialog.setPreferredSize(new Dimension(DIALOG_BOX_WIDTH, DIALOG_BOX_HEIGHT));
        dialog.pack(); // Respects preferredSize but allows it to be larger if needed based on the text

        setDialogPosition(dialog);
        return dialog;
    }

    private static JPanel createButtons(final boolean[] userInputs, final JDialog dialog) {
        final JButton continueButton = new JButton(BUTTON_CONTINUE_TEXT);
        continueButton.addActionListener(_ -> {
            dialog.dispose();
            userInputs[0] = true;
        });
        final JButton closedButton = new JButton(BUTTON_EXIT_TEXT);
        closedButton.addActionListener(_ -> {
            dialog.dispose();
            userInputs[0] = false; // Assumes there was no user input, even if some action was taken on the webpage
        });

        final JPanel buttonPanel = new JPanel(new GridLayout(1, 2));
        buttonPanel.add(continueButton);
        buttonPanel.add(closedButton);
        return buttonPanel;
    }

    private static void showDialog(final JDialog dialog, final boolean[] userInputs) {
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
