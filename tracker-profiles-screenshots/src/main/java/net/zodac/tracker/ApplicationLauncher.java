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

import java.io.IOException;
import java.sql.SQLException;
import java.util.Map;
import net.zodac.tracker.framework.ExitState;
import net.zodac.tracker.framework.TrackerHandlerFactory;
import net.zodac.tracker.framework.config.ApplicationConfiguration;
import net.zodac.tracker.framework.config.Configuration;
import net.zodac.tracker.framework.driver.DriverPool;
import net.zodac.tracker.web.WebServer;
import net.zodac.tracker.web.log.WebLogAppender;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Main class, which launches the application.
 */
public final class ApplicationLauncher {

    private static final Logger LOGGER = LogManager.getLogger();

    /**
     * The system property that, when {@code true}, starts and immediately stops the application, loading the classes used by a normal start. Used
     * only when building the Docker image, to generate the AppCDS archive.
     */
    private static final String APPCDS_TRAINING_PROPERTY = "tracker-profiles.appcds-training";

    private static final String BROWSER_RESOLUTION_REPLACEMENT = "headless browsers use 1920x1080, and browsers with a UI the screen of the web UI";
    private static final String USER_INPUT_REPLACEMENT = "a user input waits until the user responds";

    // Environment variables that are no longer used, with what replaces them
    private static final Map<String, String> REMOVED_ENVIRONMENT_VARIABLES = Map.ofEntries(
        Map.entry("BROWSER_HEIGHT", BROWSER_RESOLUTION_REPLACEMENT),
        Map.entry("BROWSER_RESOLUTION", BROWSER_RESOLUTION_REPLACEMENT),
        Map.entry("BROWSER_WIDTH", BROWSER_RESOLUTION_REPLACEMENT),
        Map.entry("CSV_COMMENT_SYMBOL", "import trackers from the web UI"),
        Map.entry("FAIL_ON_UNSUPPORTED_TRACKER", "import trackers from the web UI"),
        Map.entry("FORCE_UI_BROWSER", "watch the browser of any tracker in progress in the web UI"),
        Map.entry("INPUT_TIMEOUT_ENABLED", USER_INPUT_REPLACEMENT),
        Map.entry("INPUT_TIMEOUT_SECONDS", USER_INPUT_REPLACEMENT),
        Map.entry("LOG_TRACKER_NAME", "filter the logs by tracker in the web UI"),
        Map.entry("PROGRESS_BAR_COMPLETE_CHARACTER", "progress is shown in the web UI"),
        Map.entry("PROGRESS_BAR_ENABLED", "progress is shown in the web UI"),
        Map.entry("PROGRESS_BAR_FORMAT", "progress is shown in the web UI"),
        Map.entry("PROGRESS_BAR_INCOMPLETE_CHARACTER", "progress is shown in the web UI"),
        Map.entry("PROGRESS_BAR_LENGTH", "progress is shown in the web UI"),
        Map.entry("SCREENSHOT_EXISTS_ACTION", "an existing screenshot is always overwritten"),
        Map.entry("TAKE_SCREENSHOT_ON_ERROR", "a failure screenshot is always taken"),
        Map.entry("TRACKER_EXECUTION_ORDER", "enable or disable each tracker in the web UI"),
        Map.entry("TRACKER_INPUT_FILE_PATH", "import trackers from the web UI"),
        Map.entry("WEB_UI_ENABLED", "the web UI is always started")
    );

    private ApplicationLauncher() {

    }

    /**
     * Main method for the application. Loads the configuration, then starts the web UI, from which screenshots are executed on request.
     */
    static void main() {
        final ApplicationConfiguration applicationConfiguration = initApplicationConfiguration();
        warnOfRemovedEnvironmentVariables();
        registerShutdownHook();

        final WebServer webServer = startWebUi(applicationConfiguration);
        if (Boolean.getBoolean(APPCDS_TRAINING_PROPERTY)) {
            LOGGER.info("AppCDS training: loaded {} tracker handlers, stopping", TrackerHandlerFactory.allHandlers().size());
            webServer.stop();
            exit(ExitState.SUCCESS);
        }
    }

    private static ApplicationConfiguration initApplicationConfiguration() {
        try {
            return Configuration.init();
        } catch (final IllegalArgumentException e) {
            LOGGER.debug("Invalid environment variable", e);
            LOGGER.error("Invalid environment variable: {}", e.getMessage());
            exit(ExitState.FAILURE);
            throw e;
        }
    }

    private static void warnOfRemovedEnvironmentVariables() {
        REMOVED_ENVIRONMENT_VARIABLES.forEach((name, replacement) -> {
            if (System.getenv(name) != null) {
                LOGGER.warn("Environment variable '{}' is no longer used ({})", name, replacement);
            }
        });
    }

    // The web server's dispatcher thread keeps the JVM running after main() returns, until the application is stopped
    private static WebServer startWebUi(final ApplicationConfiguration applicationConfiguration) {
        final WebLogAppender webLogAppender = WebLogAppender.install();
        try {
            final WebServer webServer = WebServer.start(applicationConfiguration, webLogAppender);
            Runtime.getRuntime().addShutdownHook(new Thread(webServer::stop, "web-server-shutdown-hook"));
            return webServer;
        } catch (final IOException | SQLException e) {
            LOGGER.debug("Unable to start web UI", e);
            LOGGER.error("Unable to start web UI: {}", e.getMessage());
            exit(ExitState.FAILURE);
            throw new IllegalStateException(e); // Unreachable, since exit() terminates the JVM
        }
    }

    // DriverPool.shutdown() is idempotent, so it remains safe to call alongside the finally block of an execution
    private static void registerShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(DriverPool::shutdown, "application-shutdown-hook"));
    }

    private static void exit(final ExitState exitState) {
        System.exit(exitState.exitCode());  // NOPMD: DoNotTerminateVM - happy to terminate here
    }
}
