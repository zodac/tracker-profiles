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

package net.zodac.tracker;

import net.zodac.tracker.framework.Configuration;
import net.zodac.tracker.framework.ExitState;
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
     * @param args input arguments, unused
     * @see ProfileScreenshotter
     */
    public static void main(final String[] args) {
        // Start application pre-requisites
        try {
            Configuration.get(); // Validate that the application configuration is valid
        } catch (final Exception e) {
            LOGGER.debug("Error starting application pre-requisites", e);
            LOGGER.error("Error starting application pre-requisites: {}", e.getMessage());
            System.exit(ExitState.FAILURE.exitCode());
        }

        try {
            final ExitState exitState = ProfileScreenshotter.executeProfileScreenshotter();
            System.exit(exitState.exitCode());
        } catch (final Exception e) {
            LOGGER.debug("Error abruptly ended execution", e);
            LOGGER.error("Error abruptly ended execution: {}", e.getMessage());
            System.exit(ExitState.FAILURE.exitCode());
        }
    }
}
