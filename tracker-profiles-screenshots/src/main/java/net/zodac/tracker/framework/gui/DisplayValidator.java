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
import java.util.Collection;
import net.zodac.tracker.framework.TrackerType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Utility class to validate that a display is available for the browsers with a UI. The Docker image starts a virtual X11 display (Xvfb) and sets
 * {@code DISPLAY} itself (see {@code start.sh}), and the browsers are shown in the web UI, so the host needs no display. Outside Docker, the
 * display of the host is used.
 */
public final class DisplayValidator {

    private static final Logger LOGGER = LogManager.getLogger();

    private DisplayValidator() {

    }

    /**
     * Validates that the UI/{@code DISPLAY} configuration is valid for the given set of loaded trackers. If the loaded trackers include at least
     * one {@link TrackerType#MANUAL} tracker to execute, a display is required, so {@link #isDisplayAvailable()} is called to verify
     * the {@code DISPLAY} environment variable is set and a connection to the X11 server can be established.
     *
     * <p>
     * {@code java.awt.headless} is never set, since it cannot be changed once AWT is initialised, and a later execution in the same JVM may need a
     * display.
     *
     * @param trackerTypes the {@link TrackerType} of each tracker to be executed
     * @return {@code true} if the display state is valid for the trackers to be executed
     */
    public static boolean isValid(final Collection<TrackerType> trackerTypes) {
        if (trackerTypes.contains(TrackerType.MANUAL)) {
            LOGGER.trace("DISPLAY needs to be set");
            return isDisplayAvailable();
        }
        return true;
    }

    private static boolean isDisplayAvailable() {
        final String display = System.getenv("DISPLAY");
        if (display == null || display.isBlank()) {
            LOGGER.error("No display is available for browsers with a UI, as the environment variable 'DISPLAY' is not set");
            return false;
        }

        LOGGER.trace("DISPLAY value: {}", display);
        try {
            final GraphicsEnvironment localGraphicsEnvironment = GraphicsEnvironment.getLocalGraphicsEnvironment();
            LOGGER.trace("Using GraphicsEnvironment: {}", localGraphicsEnvironment);
            return true;
        } catch (final AWTError e) {
            LOGGER.debug("Unable to connect to X11 display '{}'", display, e);
            LOGGER.error("Unable to connect to X11 display '{}' for browsers with a UI", display);
            return false;
        }
    }
}
