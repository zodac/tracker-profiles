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
import java.awt.GraphicsConfiguration;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.lang.reflect.InvocationTargetException;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import net.zodac.tracker.framework.config.ApplicationConfiguration;
import net.zodac.tracker.framework.config.Configuration;
import net.zodac.tracker.framework.exception.CancelledInputException;
import net.zodac.tracker.framework.exception.NoUserInputException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jspecify.annotations.Nullable;
import org.openqa.selenium.remote.RemoteWebDriver;

/**
 * Utility class used to display pop-up windows and confirmation boxes to the user.
 */
public final class DisplayUtils {

    private static final ApplicationConfiguration CONFIG = Configuration.get();
    private static final Logger LOGGER = LogManager.getLogger();

    // UI element constants
    private static final String BUTTON_CONTINUE_TEXT = "Continue";
    private static final String BUTTON_EXIT_TEXT = "Exit";
    private static final String TITLE_SUFFIX = " Manual Input";
    private static final int DIALOG_HEIGHT = 125;
    private static final int DIALOG_WIDTH = 500;
    private static final int DIALOG_POSITION_LEFT_MARGIN = 40;
    private static final long PAGE_POLL_INTERVAL_MS = 500L;

    private static final ScheduledExecutorService TIMEOUT_SCHEDULER = Executors.newSingleThreadScheduledExecutor(runnable -> {
        final Thread t = new Thread(runnable, "dialog-timeout");
        t.setDaemon(true);
        return t;
    });

    private final RemoteWebDriver driver;

    private DisplayUtils(final RemoteWebDriver driver) {
        this.driver = driver;
    }

    /**
     * Creates a {@link DisplayUtils} bound to the given {@link RemoteWebDriver}.
     *
     * @param driver the {@link RemoteWebDriver} used to poll for page changes
     * @return the {@link DisplayUtils}
     * @see DialogPosition#ofDefault()
     */
    public static DisplayUtils withDriver(final RemoteWebDriver driver) {
        return new DisplayUtils(driver);
    }

    /**
     * Similar to the {@link #confirm(String, String, DialogPosition)}, but using {@link DialogPosition#ofDefault()}.
     *
     * @param title the title for the pop-up
     * @param label the text for the pop-up
     * @see #confirm(String, String, DialogPosition)
     */
    public void confirm(final String title, final String label) {
        confirm(title, label, DialogPosition.ofDefault());
    }

    /**
     * Creates a pop-up on the screen for the user to click to confirm a user input has been provided to the loaded tracker.
     *
     * @param title the title for the pop-up
     * @param label the text for the pop-up
     */
    public void confirm(final String title, final String label, final DialogPosition dialogPosition) {
        setStyleToSystemTheme();

        final AtomicBoolean userInput = new AtomicBoolean(false);
        final AtomicBoolean timedOut = new AtomicBoolean(false);

        final String initialUrl = driver.getCurrentUrl();
        final String initialTitle = driver.getTitle();

        final JDialog dialog = createDialog(title, label, userInput, dialogPosition);
        showDialog(dialog, userInput, timedOut, initialUrl, initialTitle);
    }

    private static JDialog createDialog(final String title, final String label, final AtomicBoolean userInput, final DialogPosition dialogPosition) {
        final JDialog dialog = new JDialog((Frame) null, title + TITLE_SUFFIX, true);
        dialog.setLayout(new BorderLayout());
        dialog.setAlwaysOnTop(true);  // Ensure the dialog remains on top of all windows when interacting with browser

        final JPanel panel = new JPanel(new GridLayout(2, 1));
        final String labelText = "<html>" + label + "</html>"; // Wrap text as HTML so .pack() can resize
        panel.add(new JLabel(labelText, SwingConstants.CENTER));

        dialog.add(panel, BorderLayout.CENTER);
        dialog.add(createButtons(dialog, userInput), BorderLayout.PAGE_END);

        dialog.setPreferredSize(new Dimension(DIALOG_WIDTH, DIALOG_HEIGHT));
        dialog.pack();  // Respects preferredSize but allows it to be larger if needed based on the text
        dialog.setLocationRelativeTo(null);

        setDialogPosition(dialog, dialogPosition);
        return dialog;
    }

    private static JPanel createButtons(final JDialog dialog, final AtomicBoolean userInput) {
        final JButton continueButton = new JButton();
        final AtomicLong remainingSeconds = new AtomicLong(CONFIG.inputTimeoutEnabled() ? CONFIG.inputTimeoutDuration().toSeconds() : 0);

        // Initial text
        if (CONFIG.inputTimeoutEnabled()) {
            continueButton.setText("%s (%s)".formatted(BUTTON_CONTINUE_TEXT, remainingSeconds.get()));
        } else {
            continueButton.setText(BUTTON_CONTINUE_TEXT);
        }

        // Countdown timer (Swing timer = EDT safe)
        final Timer countdownTimer = startCountdownTimer(remainingSeconds, continueButton);

        continueButton.addActionListener(_ -> {
            if (countdownTimer != null) {
                countdownTimer.stop();
            }
            userInput.set(true);
            dialog.dispose();
        });

        final JButton exitButton = new JButton(BUTTON_EXIT_TEXT);
        exitButton.addActionListener(_ -> {
            if (countdownTimer != null) {
                countdownTimer.stop();
            }
            dialog.dispose();
        });

        final JPanel buttonPanel = new JPanel(new GridLayout(1, 2));
        buttonPanel.add(continueButton);
        buttonPanel.add(exitButton);
        return buttonPanel;
    }

    private static @Nullable Timer startCountdownTimer(final AtomicLong remainingSeconds, final JButton continueButton) {
        if (!CONFIG.inputTimeoutEnabled()) {
            return null;
        }

        final int timerDelay = ((Long) Duration.ofSeconds(1L).toMillis()).intValue();
        final Timer countdownTimer = new Timer(timerDelay, event -> {
            final long value = remainingSeconds.decrementAndGet();
            if (value > 0) {
                continueButton.setText("%s (%s)".formatted(BUTTON_CONTINUE_TEXT, value));
            } else {
                continueButton.setText(BUTTON_CONTINUE_TEXT);
                ((Timer) event.getSource()).stop();
            }
        });
        countdownTimer.setInitialDelay(timerDelay);
        countdownTimer.start();
        return countdownTimer;
    }

    private void showDialog(final JDialog dialog, final AtomicBoolean userProvidedInput, final AtomicBoolean timedOut,
                            final @Nullable String initialUrl, final @Nullable String initialTitle) {
        final ScheduledFuture<?> timeoutTask = getTimeoutTask(dialog, timedOut);
        final ScheduledFuture<?> pageChangeTask = getPageChangeTask(dialog, userProvidedInput, initialUrl, initialTitle);

        try {
            SwingUtilities.invokeAndWait(() -> dialog.setVisible(true));

            if (userProvidedInput.get()) {
                LOGGER.trace("Received an input");
                return;
            }

            if (CONFIG.inputTimeoutEnabled() && timedOut.get()) {
                LOGGER.trace("Dialog timed out and no input received");
                throw new NoUserInputException(CONFIG.inputTimeoutDuration(), new IllegalStateException());
            }

            LOGGER.trace("Dialog still running and no timeout received, assuming the user cancelled execution");
            throw new CancelledInputException();
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new NoUserInputException(CONFIG.inputTimeoutDuration(), e);
        } catch (final InvocationTargetException e) {
            throw new NoUserInputException(CONFIG.inputTimeoutDuration(), e.getCause() == null ? e : e.getCause());
        } finally {
            if (timeoutTask != null) {
                timeoutTask.cancel(true);
            }
            pageChangeTask.cancel(true);
            dialog.dispose();
        }
    }

    private ScheduledFuture<?> getPageChangeTask(final JDialog dialog, final AtomicBoolean userProvidedInput,
                                                 final @Nullable String initialUrl, final @Nullable String initialTitle) {
        LOGGER.trace("Starting page change listener (initial url: '{}', initial title: '{}')", initialUrl, initialTitle);
        return TIMEOUT_SCHEDULER.scheduleAtFixedRate(() -> {
            try {
                final String currentUrl = driver.getCurrentUrl();
                final String currentTitle = driver.getTitle();
                if (!Objects.equals(currentUrl, initialUrl) || !Objects.equals(currentTitle, initialTitle)) {
                    LOGGER.trace("Page change detected (url: '{}' -> '{}', title: '{}' -> '{}'), auto-closing dialog",
                        initialUrl, currentUrl, initialTitle, currentTitle);
                    userProvidedInput.set(true);
                    SwingUtilities.invokeLater(dialog::dispose);
                }
            } catch (final Exception e) {
                LOGGER.trace("Error polling for page change", e);
            }
        }, PAGE_POLL_INTERVAL_MS, PAGE_POLL_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    private static @Nullable ScheduledFuture<?> getTimeoutTask(final JDialog dialog, final AtomicBoolean timedOut) {
        if (!CONFIG.inputTimeoutEnabled()) {
            LOGGER.trace("Dialog timeouts disabled via TIMEOUT_DIALOGS=false");
            return null;
        }

        final ScheduledFuture<?> timeoutTask = TIMEOUT_SCHEDULER.schedule(
            () -> SwingUtilities.invokeLater(() -> {
                if (dialog.isShowing()) {
                    timedOut.set(true);
                    dialog.dispose();
                }
            }), CONFIG.inputTimeoutDuration().toSeconds(), TimeUnit.SECONDS
        );

        LOGGER.trace("Waiting {} for user input", CONFIG.inputTimeoutDuration());
        return timeoutTask;
    }

    private static void setDialogPosition(final JDialog dialog, final DialogPosition dialogPosition) {
        final GraphicsConfiguration gc = dialog.getGraphicsConfiguration();
        final Rectangle bounds = gc.getBounds();

        // Horizontal: DIALOG_POSITION_LEFT_MARGIN is the baseline; horizontalPercent distributes remaining space right of that margin
        final int availableWidth = bounds.width - dialog.getWidth() - DIALOG_POSITION_LEFT_MARGIN;
        final int x = bounds.x + DIALOG_POSITION_LEFT_MARGIN + (int) (availableWidth * dialogPosition.horizontalPercent() / 100.0);

        // Vertical: verticalPercent distributes the full available vertical space (default 60% keeps clear of top-of-page elements)
        final int availableHeight = bounds.height - dialog.getHeight();
        final int y = bounds.y + (int) (availableHeight * dialogPosition.verticalPercent() / 100.0);

        dialog.setLocation(x, y);
    }

    private static void setStyleToSystemTheme() {
        try {
            FlatDarkLaf.setup();
            UIManager.setLookAndFeel(new FlatDarkLaf());
        } catch (final UnsupportedLookAndFeelException e) {
            LOGGER.debug("Unexpected error setting UI style", e);
        }
    }
}
