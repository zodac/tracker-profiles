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

package net.zodac.tracker.framework.gui;

import com.formdev.flatlaf.FlatDarkLaf;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.GraphicsEnvironment;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.WindowConstants;
import net.zodac.tracker.framework.input.UserInputPrompt;
import net.zodac.tracker.framework.input.UserInputRequests;
import net.zodac.tracker.framework.input.UserInputRequests.PendingUserInput;
import net.zodac.tracker.framework.input.UserInputResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * A {@link UserInputPrompt} shown as a Swing dialog, always on top of the browser of the tracker, so the user can respond without switching back
 * to the web UI. It responds to the request in the same way as the web UI, and is closed once the request is no longer waiting (whether answered
 * here, in the web UI, by a page change, a timeout or a cancelled execution).
 *
 * <p>
 * If no display is available, nothing is shown, and the request is only shown in the web UI.
 */
public final class InputDialog implements UserInputPrompt {

    private static final Logger LOGGER = LogManager.getLogger();

    private static final String TITLE_SUFFIX = " Manual Input";
    private static final int DIALOG_HEIGHT = 125;
    private static final int DIALOG_WIDTH = 500;
    private static final int DIALOG_LEFT_MARGIN = 40;
    private static final double DIALOG_VERTICAL_POSITION = 0.6d; // Keeps clear of the top of the page, where most login forms are
    private static final int TIMER_INTERVAL_MILLIS = 1_000;
    private static final AtomicBoolean LOOK_AND_FEEL_SET = new AtomicBoolean(false);

    private final String trackerName;
    private final String message;

    private InputDialog(final String trackerName, final String message) {
        this.trackerName = trackerName;
        this.message = message;
    }

    /**
     * Creates an {@link InputDialog} for a request from a tracker.
     *
     * @param trackerName the name of the tracker waiting for the input
     * @param message     what the user needs to do
     * @return the {@link InputDialog}
     */
    public static InputDialog of(final String trackerName, final String message) {
        return new InputDialog(trackerName, message);
    }

    @Override
    public Runnable show(final long requestId) {
        if (GraphicsEnvironment.isHeadless()) {
            LOGGER.trace("No display available, the input is only requested in the web UI");
            return () -> {
            };
        }

        final AtomicReference<JDialog> dialogReference = new AtomicReference<>();
        final AtomicReference<Timer> timerReference = new AtomicReference<>();
        SwingUtilities.invokeLater(() -> {
            // The request may already have finished before the dialog is created
            if (UserInputRequests.find(requestId).isEmpty()) {
                return;
            }

            try {
                setLookAndFeel();
                final JLabel timerLabel = new JLabel("", SwingConstants.CENTER);
                final JDialog dialog = createDialog(requestId, timerLabel);
                final Timer timer = new Timer(TIMER_INTERVAL_MILLIS, _ -> updateTimer(requestId, timerLabel));
                updateTimer(requestId, timerLabel);
                timer.start();

                dialogReference.set(dialog);
                timerReference.set(timer);
                dialog.setVisible(true);
            } catch (final RuntimeException e) {
                LOGGER.debug("Unable to show the input dialog, the input is only requested in the web UI", e);
            }
        });

        // Run on the event dispatch thread after the dialog is created, as the queue is in order
        return () -> SwingUtilities.invokeLater(() -> {
            final Timer timer = timerReference.get();
            if (timer != null) {
                timer.stop();
            }

            final JDialog dialog = dialogReference.get();
            if (dialog != null) {
                dialog.dispose();
            }
        });
    }

    private JDialog createDialog(final long requestId, final JLabel timerLabel) {
        final JDialog dialog = new JDialog((Frame) null, trackerName + TITLE_SUFFIX, false);
        dialog.setLayout(new BorderLayout());
        dialog.setAlwaysOnTop(true);  // Keep the dialog in front of the browser while the user interacts with it
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);  // Closing the dialog leaves the request in the web UI

        final JPanel textPanel = new JPanel(new GridLayout(2, 1));
        // Wrapped as HTML so pack() can resize the dialog for longer text
        textPanel.add(new JLabel("<html>%s, then select Continue.</html>".formatted(escapeHtml(message)), SwingConstants.CENTER));
        textPanel.add(timerLabel);
        dialog.add(textPanel, BorderLayout.CENTER);
        dialog.add(createButtons(requestId), BorderLayout.PAGE_END);

        dialog.setPreferredSize(new Dimension(DIALOG_WIDTH, DIALOG_HEIGHT));
        dialog.pack();  // Respects the preferred size, but allows it to be larger if needed for the text
        setPosition(dialog);
        return dialog;
    }

    private JPanel createButtons(final long requestId) {
        final JButton continueButton = new JButton("Continue");
        continueButton.addActionListener(_ -> respond(requestId, UserInputResponse.CONTINUE));

        final JButton skipButton = new JButton("Skip tracker");
        skipButton.setToolTipText("Fail this attempt for the tracker, and move on");
        skipButton.addActionListener(_ -> respond(requestId, UserInputResponse.SKIP));

        final JPanel buttonPanel = new JPanel(new GridLayout(1, 2));
        buttonPanel.add(continueButton);
        buttonPanel.add(skipButton);
        return buttonPanel;
    }

    // The dialog is closed by the waiting request once it has the response
    private void respond(final long requestId, final UserInputResponse response) {
        if (!UserInputRequests.respond(requestId, response)) {
            LOGGER.trace("Request #{} had already finished, ignoring {} from the input dialog", requestId, response);
        }
    }

    // The expiry is read every time, so an extension made in the web UI is shown
    private static void updateTimer(final long requestId, final JLabel timerLabel) {
        final Long expiresAt = UserInputRequests.find(requestId)
            .map(PendingUserInput::expiresAt)
            .orElse(null);

        if (expiresAt == null) {
            timerLabel.setText("");
            return;
        }

        final Duration remaining = Duration.ofMillis(Math.max(0L, expiresAt - System.currentTimeMillis()));
        timerLabel.setText("Time remaining: %d:%02d".formatted(remaining.toMinutes(), remaining.toSecondsPart()));
    }

    // Left-aligned, part way down the screen, so the dialog does not cover the login form of the browser
    private static void setPosition(final JDialog dialog) {
        final Rectangle bounds = dialog.getGraphicsConfiguration().getBounds();
        final int x = bounds.x + DIALOG_LEFT_MARGIN;
        final int y = bounds.y + (int) ((bounds.height - dialog.getHeight()) * DIALOG_VERTICAL_POSITION);
        dialog.setLocation(x, y);
    }

    private static void setLookAndFeel() {
        if (LOOK_AND_FEEL_SET.compareAndSet(false, true)) {
            FlatDarkLaf.setup();
        }
    }

    private static String escapeHtml(final String text) {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;");
    }
}
