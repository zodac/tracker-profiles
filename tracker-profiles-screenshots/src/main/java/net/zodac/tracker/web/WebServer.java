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
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.Set;
import java.util.concurrent.Executors;
import net.zodac.tracker.framework.TrackerCredential;
import net.zodac.tracker.framework.TrackerCsvReader;
import net.zodac.tracker.framework.TrackerHandlerFactory;
import net.zodac.tracker.framework.annotation.TrackerHandler;
import net.zodac.tracker.framework.config.ApplicationConfiguration;
import net.zodac.tracker.web.http.LogStreamHandler;
import net.zodac.tracker.web.http.RunApiHandler;
import net.zodac.tracker.web.http.SettingsApiHandler;
import net.zodac.tracker.web.http.StaticResourceHandler;
import net.zodac.tracker.web.http.TrackerApiHandler;
import net.zodac.tracker.web.log.WebLogAppender;
import net.zodac.tracker.web.storage.ApplicationDatabase;
import net.zodac.tracker.web.storage.StoredTrackerCredential;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * The web UI server. Serves the static web UI and its JSON API using the JDK {@link HttpServer}, handling each request on a virtual thread.
 */
public final class WebServer {

    private static final Logger LOGGER = LogManager.getLogger();
    private static final int STOP_DELAY_SECONDS = 1;

    private final HttpServer httpServer;

    private WebServer(final HttpServer httpServer) {
        this.httpServer = httpServer;
    }

    /**
     * Opens the {@link ApplicationDatabase} (importing the tracker CSV file if no trackers are stored yet), then starts the web UI server.
     *
     * @param applicationConfiguration the {@link ApplicationConfiguration} loaded from environment variables
     * @param webLogAppender           the {@link WebLogAppender} capturing log messages to stream to the web UI
     * @return the started {@link WebServer}
     * @throws IOException  thrown if the server cannot be started
     * @throws SQLException thrown if the database cannot be opened
     */
    public static WebServer start(final ApplicationConfiguration applicationConfiguration, final WebLogAppender webLogAppender)
        throws IOException, SQLException {
        final ApplicationDatabase applicationDatabase = ApplicationDatabase.open(applicationConfiguration.databasePath());
        importCsvIfNoTrackersStored(applicationDatabase, applicationConfiguration.trackerInputFilePath());
        applyStoredSettings(applicationDatabase);

        final RunManager runManager = RunManager.create(applicationDatabase);
        final SettingsService settingsService = SettingsService.create(applicationDatabase);

        final HttpServer httpServer = HttpServer.create(new InetSocketAddress(applicationConfiguration.webUiPort()), 0);
        httpServer.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        httpServer.createContext(RunApiHandler.CONTEXT_PATH, RunApiHandler.create(runManager));
        httpServer.createContext(LogStreamHandler.CONTEXT_PATH, LogStreamHandler.create(webLogAppender));
        httpServer.createContext(TrackerApiHandler.CONTEXT_PATH, TrackerApiHandler.create(applicationDatabase));
        httpServer.createContext(SettingsApiHandler.CONTEXT_PATH, SettingsApiHandler.create(settingsService));
        httpServer.createContext(StaticResourceHandler.CONTEXT_PATH, StaticResourceHandler.create());
        httpServer.start();

        LOGGER.info("Web UI available on port {}", applicationConfiguration.webUiPort());
        return new WebServer(httpServer);
    }

    /**
     * Stops the server, allowing a short delay for in-flight requests to complete.
     */
    public void stop() {
        httpServer.stop(STOP_DELAY_SECONDS);
    }

    private static void applyStoredSettings(final ApplicationDatabase applicationDatabase) throws SQLException {
        try {
            RunManager.reloadConfiguration(applicationDatabase);
        } catch (final IllegalArgumentException e) {
            LOGGER.warn("Settings saved in the web UI are invalid with the current environment variables (checked again on each execution): {}",
                e.getMessage());
        }
    }

    // Stores trackers using the name defined by their handler, rather than the casing used in the CSV file
    private static String canonicalName(final String trackerName) {
        return TrackerHandlerFactory.findMatchingHandler(trackerName)
            .map(TrackerHandler::name)
            .orElse(trackerName);
    }

    private static void importCsvIfNoTrackersStored(final ApplicationDatabase applicationDatabase, final Path trackerInputFilePath)
        throws SQLException {
        if (applicationDatabase.hasCredentials()) {
            LOGGER.trace("Trackers already stored, not importing CSV file");
            return;
        }

        if (!Files.isRegularFile(trackerInputFilePath)) {
            LOGGER.debug("No trackers stored and no CSV file at '{}' to import", trackerInputFilePath);
            return;
        }

        try {
            final Set<TrackerCredential> trackerCredentials = TrackerCsvReader.readTrackerCredentials();
            applicationDatabase.saveCredentials(trackerCredentials
                .stream()
                .map(credential -> new StoredTrackerCredential(canonicalName(credential.name()), credential.username(), credential.password(), true))
                .toList());
            LOGGER.info("Imported {} trackers from '{}'", trackerCredentials.size(), trackerInputFilePath);
        } catch (final IOException | RuntimeException e) {
            LOGGER.debug("Unable to import trackers from CSV file '{}'", trackerInputFilePath, e);
            LOGGER.warn("Unable to import trackers from CSV file '{}': {}", trackerInputFilePath, e.getMessage());
        }
    }
}
