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

import java.awt.AWTError;
import java.awt.GraphicsEnvironment;
import java.util.Map;
import java.util.Set;
import net.zodac.tracker.framework.TrackerCredential;
import net.zodac.tracker.framework.TrackerType;
import net.zodac.tracker.framework.config.ApplicationConfiguration;
import net.zodac.tracker.framework.config.Configuration;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Utility class to validate the UI/{@code DISPLAY} configuration is valid.
 */
public final class DisplayValidator {

    private static final Logger LOGGER = LogManager.getLogger();

    private DisplayValidator() {

    }

    /**
     * Validates that the UI/{@code DISPLAY} configuration is valid for the given set of loaded trackers. There are three possible outcomes:
     *
     * <ul>
     *   <li>If {@link ApplicationConfiguration#forceUiBrowser()} is {@code true}, or the loaded trackers include at least one
     *       {@link TrackerType#MANUAL} tracker, a display is required. {@link #isDisplayAvailable()} is called to verify the
     *       {@code DISPLAY} environment variable is set and a connection to the X11 server can be established.</li>
     *   <li>If no display is required, {@code java.awt.headless} is set to {@code true} so that AWT image operations (used during
     *       screenshotting) work without an X11 connection, even if a {@code DISPLAY} value is set in the environment.</li>
     * </ul>
     *
     * @param trackersByType the loaded trackers grouped by {@link TrackerType}
     * @return {@code true} if the display state is valid for the trackers to be executed
     */
    public static boolean isValid(final Map<TrackerType, Set<TrackerCredential>> trackersByType) {
        return isValid(trackersByType, true);
    }

    /**
     * Validates that the UI/{@code DISPLAY} configuration is valid for the given set of loaded trackers, as with {@link #isValid(Map)}.
     *
     * <p>
     * Once AWT is initialised, {@code java.awt.headless} cannot be changed. So when multiple executions run in the same JVM (as from the web UI),
     * {@code forceHeadlessWhenNotNeeded} should be {@code false}, otherwise a headless-only execution would prevent any later execution from
     * using a display.
     *
     * @param trackersByType             the loaded trackers grouped by {@link TrackerType}
     * @param forceHeadlessWhenNotNeeded whether to set {@code java.awt.headless} to {@code true} when no display is required
     * @return {@code true} if the display state is valid for the trackers to be executed
     */
    public static boolean isValid(final Map<TrackerType, Set<TrackerCredential>> trackersByType, final boolean forceHeadlessWhenNotNeeded) {
        if (needsDisplay(trackersByType)) {
            LOGGER.trace("DISPLAY needs to be set");
            return isDisplayAvailable();
        }

        // If only using headless trackers (and not forcing UI), ignore any DISPLAY value
        if (forceHeadlessWhenNotNeeded) {
            System.setProperty("java.awt.headless", "true");
        }
        return true;
    }

    private static boolean needsDisplay(final Map<TrackerType, Set<TrackerCredential>> trackersByType) {
        return Configuration.get().forceUiBrowser()
            || (Configuration.get().trackerExecutionOrder().contains(TrackerType.MANUAL) && trackersByType.containsKey(TrackerType.MANUAL));
    }

    private static boolean isDisplayAvailable() {
        final String display = System.getenv("DISPLAY");
        if (display == null || display.isBlank()) {
            LOGGER.error("Environment variable 'DISPLAY' is not configured");
            return false;
        }

        LOGGER.trace("DISPLAY value: {}", display);
        try {
            final GraphicsEnvironment localGraphicsEnvironment = GraphicsEnvironment.getLocalGraphicsEnvironment();
            LOGGER.trace("Using GraphicsEnvironment: {}", localGraphicsEnvironment);
            return true;
        } catch (final AWTError e) {
            LOGGER.debug("Unable to connect to X11 display '{}'", display, e);
            LOGGER.error("Unable to connect to X11 display '{}'", display);
            return false;
        }
    }
}
