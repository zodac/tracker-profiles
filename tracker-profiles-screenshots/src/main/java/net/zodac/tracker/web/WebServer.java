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

package net.zodac.tracker.web;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import net.zodac.tracker.framework.config.ApplicationConfiguration;
import net.zodac.tracker.framework.config.ConfigurationOption;
import net.zodac.tracker.web.backup.BackupService;
import net.zodac.tracker.web.http.BackupApiHandler;
import net.zodac.tracker.web.http.BrowserApiHandler;
import net.zodac.tracker.web.http.LogApiHandler;
import net.zodac.tracker.web.http.RunApiHandler;
import net.zodac.tracker.web.http.ScreenshotApiHandler;
import net.zodac.tracker.web.http.SettingsApiHandler;
import net.zodac.tracker.web.http.StaticResourceHandler;
import net.zodac.tracker.web.http.TrackerApiHandler;
import net.zodac.tracker.web.log.WebLogAppender;
import net.zodac.tracker.web.storage.ApplicationDatabase;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jspecify.annotations.Nullable;

/**
 * The web UI server. Serves the static web UI and its JSON API using the JDK {@link HttpServer}, handling each request on a virtual thread.
 */
public final class WebServer {

    private static final Logger LOGGER = LogManager.getLogger();
    private static final int STOP_DELAY_SECONDS = 1;

    private final HttpServer httpServer;
    private final ApplicationDatabase applicationDatabase;
    private final AtomicBoolean stopped = new AtomicBoolean();

    private WebServer(final HttpServer httpServer, final ApplicationDatabase applicationDatabase) {
        this.httpServer = httpServer;
        this.applicationDatabase = applicationDatabase;
    }

    /**
     * Opens the {@link ApplicationDatabase} (removing any setting saved for an option that no longer exists, or can only be set as an environment
     * variable), then starts the web UI server. The database is closed again if the server cannot be started, and otherwise by {@link #stop()}.
     *
     * @param applicationConfiguration the {@link ApplicationConfiguration} loaded from environment variables
     * @param webLogAppender           the {@link WebLogAppender} capturing the log messages shown in the web UI
     * @return the started {@link WebServer}
     * @throws IOException  thrown if the server cannot be started
     * @throws SQLException thrown if the database cannot be opened
     */
    public static WebServer start(final ApplicationConfiguration applicationConfiguration, final WebLogAppender webLogAppender)
        throws IOException, SQLException {
        final ApplicationDatabase applicationDatabase = ApplicationDatabase.open(applicationConfiguration.databasePath(),
            applicationConfiguration.secretKey());
        try {
            removeObsoleteSettings(applicationDatabase);
            applyStoredSettings(applicationDatabase);

            final RunManager runManager = RunManager.create(applicationDatabase, webLogAppender);
            final SettingsService settingsService = SettingsService.create(applicationDatabase, runManager::isExecutionInProgress,
                runManager::applySavedSettings);

            final HttpServer httpServer = HttpServer.create(new InetSocketAddress(applicationConfiguration.webUiPort()), 0);
            httpServer.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
            httpServer.createContext(RunApiHandler.CONTEXT_PATH, RunApiHandler.create(runManager));
            httpServer.createContext(BrowserApiHandler.CONTEXT_PATH, BrowserApiHandler.create(runManager));
            httpServer.createContext(LogApiHandler.CONTEXT_PATH, LogApiHandler.create(webLogAppender));
            httpServer.createContext(ScreenshotApiHandler.CONTEXT_PATH, ScreenshotApiHandler.create(runManager));
            httpServer.createContext(TrackerApiHandler.CONTEXT_PATH, TrackerApiHandler.create(applicationDatabase, runManager));
            httpServer.createContext(SettingsApiHandler.CONTEXT_PATH, SettingsApiHandler.create(settingsService));
            httpServer.createContext(BackupApiHandler.CONTEXT_PATH, BackupApiHandler.create(BackupService.create(applicationDatabase, runManager)));
            httpServer.createContext(StaticResourceHandler.CONTEXT_PATH, StaticResourceHandler.create());
            httpServer.start();

            LOGGER.info("Web UI available at http://127.0.0.1:{}", applicationConfiguration.webUiPort());
            return new WebServer(httpServer, applicationDatabase);
        } catch (final IOException | SQLException | RuntimeException e) {
            applicationDatabase.close();
            throw e;
        }
    }

    /**
     * Stops the server, allowing a short delay for in-flight requests to complete, then closes the {@link ApplicationDatabase}. Only the first call
     * has any effect.
     */
    public void stop() {
        if (!stopped.compareAndSet(false, true)) {
            return;
        }

        LOGGER.debug("Stopping web server in {} seconds", STOP_DELAY_SECONDS);
        httpServer.stop(STOP_DELAY_SECONDS);
        try {
            applicationDatabase.close();
        } catch (final SQLException e) {
            LOGGER.debug("Unable to close the database", e);
            LOGGER.warn("Unable to close the database: {}", e.getMessage());
        }
    }

    private static void removeObsoleteSettings(final ApplicationDatabase applicationDatabase) throws SQLException {
        final Map<String, String> savedSettings = applicationDatabase.settings().findAll();
        final Map<String, String> currentSettings = new LinkedHashMap<>(savedSettings);
        currentSettings.keySet().removeIf(name -> !isEditable(ConfigurationOption.find(name)));

        if (currentSettings.size() == savedSettings.size()) {
            return;
        }

        LOGGER.debug("Removing saved settings for options that no longer exist or are only set as environment variables: {}", savedSettings
            .keySet()
            .stream()
            .filter(name -> !currentSettings.containsKey(name))
            .toList());
        applicationDatabase.settings().replaceAll(currentSettings);
    }

    private static boolean isEditable(final @Nullable ConfigurationOption option) {
        return option != null && option.editableAtRuntime();
    }

    private static void applyStoredSettings(final ApplicationDatabase applicationDatabase) throws SQLException {
        try {
            RunManager.reloadConfiguration(applicationDatabase);
        } catch (final IllegalArgumentException e) {
            LOGGER.warn("Settings saved in the web UI are invalid with the current environment variables (checked again on each execution): {}",
                e.getMessage());
        }
    }
}
