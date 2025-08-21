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

package net.zodac.tracker.framework.driver.python;

import com.google.gson.Gson;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import net.zodac.tracker.framework.exception.DriverAttachException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Utility class used to send HTTP requests or parse HTTP responses from the Python web server for Selenium sessions.
 */
final class PythonHttpServerHandler {

    private static final Logger LOGGER = LogManager.getLogger();

    // URLs
    private static final String BASE_URL = "http://localhost:5000";
    private static final String CLOSE_URL = BASE_URL + "/close";
    private static final String OPEN_URL = BASE_URL + "/open";

    // JSON payloads
    private static final String CLOSE_REQUEST_PAYLOAD_FORMAT = """
        {
            "session_id": "%s"
        }
        """;
    private static final String OPEN_REQUEST_PAYLOAD_FORMAT = """
        {
            "browser_data_storage_path": "%s",
            "browser_dimensions": "%s"
        }""";

    // Header names and values
    private static final String CONTENT_TYPE_HEADER_NAME = "Content-Type";
    private static final String CONTENT_TYPE_JSON_VALUE = "application/json";

    private static final Gson GSON = new Gson();
    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

    private PythonHttpServerHandler() {

    }

    /**
     * Opens a Python-based Selenium web browser session.
     *
     * @param browserDataStoragePath the file path in which to store browser data (profiles, caches, etc.)
     * @param browserDimensions      the dimensions in the format {@code width,height} for the {@code Selenium} web browser
     * @return the {@link SeleniumSession} of the Python browser session
     * @see net.zodac.tracker.framework.ApplicationConfiguration#browserDataStoragePath()
     * @see net.zodac.tracker.framework.ApplicationConfiguration#browserDimensions()
     */
    static SeleniumSession openSession(final String browserDataStoragePath, final String browserDimensions) {
        final String jsonPayload = OPEN_REQUEST_PAYLOAD_FORMAT.formatted(browserDataStoragePath, browserDimensions);

        try {
            final HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(OPEN_URL))
                .header(CONTENT_TYPE_HEADER_NAME, CONTENT_TYPE_JSON_VALUE)
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .build();

            LOGGER.debug("Sending request to open session to '{}'", OPEN_URL);
            final HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != HttpURLConnection.HTTP_OK) {
                LOGGER.warn("Request to open session responded with status {}, body: {}", response.statusCode(), response.body());
                throw new DriverAttachException("Error opening Python session to attach driver");
            }

            final SeleniumSession seleniumSession = GSON.fromJson(response.body(), SeleniumSession.class);
            LOGGER.trace("Session details: {}", seleniumSession);
            return seleniumSession;
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DriverAttachException(e);
        } catch (final IOException e) {
            throw new DriverAttachException(e);
        }
    }

    /**
     * Closes a Python-based Selenium web browser session.
     *
     * @param seleniumSession the {@link SeleniumSession} to close
     */
    static void closeSession(final SeleniumSession seleniumSession) {
        final String jsonPayload = CLOSE_REQUEST_PAYLOAD_FORMAT.formatted(seleniumSession.sessionId());

        try {
            final HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(CLOSE_URL))
                .header(CONTENT_TYPE_HEADER_NAME, CONTENT_TYPE_JSON_VALUE)
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .build();

            LOGGER.debug("Sending request to close session to '{}'", CLOSE_URL);
            final HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != HttpURLConnection.HTTP_OK) {
                LOGGER.warn("Request to close session {} responded with status {}, body: {}", seleniumSession, response.statusCode(),
                    response.body());
            }
            LOGGER.trace("Request to close session successfully completed");
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.debug("Error closing Python browser session", e);
            LOGGER.warn("Error closing Python browser session: {}", e.getMessage());
        } catch (final IOException e) {
            LOGGER.debug("Error closing Python browser session", e);
            LOGGER.warn("Error closing Python browser session: {}", e.getMessage());
        } catch (final Exception e) {
            LOGGER.debug("Unexpected error closing Python browser session", e);
            LOGGER.warn("Unexpected error closing Python browser session: {}", e.getMessage());
        }
    }
}
