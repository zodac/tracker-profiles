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

package net.zodac.tracker;

import java.util.Map;
import java.util.Set;
import net.zodac.tracker.app.ScreenshotOrchestrator;
import net.zodac.tracker.app.TrackerRetriever;
import net.zodac.tracker.framework.ExitState;
import net.zodac.tracker.framework.TrackerCredential;
import net.zodac.tracker.framework.TrackerType;
import net.zodac.tracker.framework.config.Configuration;
import net.zodac.tracker.framework.gui.DisplayValidator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Main class, which launches the application.
 */
public final class ApplicationLauncher {

    private static final Logger LOGGER = LogManager.getLogger();

    private ApplicationLauncher() {

    }

    /**
     * Main method for the application. Configures any requirements then launches the application.
     *
     * @see ScreenshotOrchestrator
     */
    // TODO: Investigate whether it's worth not logging out
    static void main() {
        initApplicationConfiguration();

        final Map<TrackerType, Set<TrackerCredential>> trackersByType = TrackerRetriever.getTrackers();
        validateDisplay(trackersByType);
        startApplication(trackersByType);
    }

    private static void initApplicationConfiguration() {
        try {
            Configuration.init();
        } catch (final IllegalArgumentException e) {
            LOGGER.debug("Invalid environment variable", e);
            LOGGER.error("Invalid environment variable: {}", e.getMessage());
            exit(ExitState.FAILURE);
        }
    }

    private static void validateDisplay(final Map<TrackerType, Set<TrackerCredential>> trackersByType) {
        if (!DisplayValidator.isValid(trackersByType)) {
            exit(ExitState.FAILURE);
        }
    }

    private static void startApplication(final Map<TrackerType, Set<TrackerCredential>> trackersByType) {
        try {
            final ExitState exitState = ScreenshotOrchestrator.start(trackersByType);
            exit(exitState);
        } catch (final Exception e) {
            LOGGER.debug("Error abruptly ended execution", e);
            LOGGER.error("Error abruptly ended execution: {}", e.getMessage());
            exit(ExitState.FAILURE);
        }
    }

    private static void exit(final ExitState exitState) {
        System.exit(exitState.exitCode());  // NOPMD: DoNotTerminateVM - happy to terminate here
    }
}
