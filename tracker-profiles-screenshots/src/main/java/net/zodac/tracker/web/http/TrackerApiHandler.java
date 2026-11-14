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

package net.zodac.tracker.web.http;

import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import net.zodac.tracker.framework.TrackerCredential;
import net.zodac.tracker.framework.TrackerCsvReader;
import net.zodac.tracker.framework.TrackerHandlerFactory;
import net.zodac.tracker.framework.annotation.TrackerHandler;
import net.zodac.tracker.framework.exception.InvalidCsvInputException;
import net.zodac.tracker.web.storage.ApplicationDatabase;
import net.zodac.tracker.web.storage.StoredTrackerCredential;
import net.zodac.tracker.web.storage.StoredTrackerResult;
import org.jspecify.annotations.Nullable;

/**
 * Handles requests to list every supported tracker with its stored credentials and most recent result ({@code GET}), save the credentials for a
 * tracker ({@code PUT}), import credentials from a CSV file ({@code POST /api/trackers/import}), and remove the stored credentials for a tracker
 * ({@code DELETE /api/trackers/{name}}).
 *
 * <p>
 * Passwords are never returned to the client; only whether a password has been saved.
 */
public final class TrackerApiHandler extends ApiHandler {

    /**
     * The context path for this handler.
     */
    public static final String CONTEXT_PATH = "/api/trackers";

    private static final String IMPORT_PATH = "import";

    private final ApplicationDatabase applicationDatabase;

    private TrackerApiHandler(final ApplicationDatabase applicationDatabase) {
        super(CONTEXT_PATH);
        this.applicationDatabase = applicationDatabase;
    }

    /**
     * Creates a {@link TrackerApiHandler}.
     *
     * @param applicationDatabase the {@link ApplicationDatabase} storing tracker credentials
     * @return the {@link TrackerApiHandler}
     */
    public static TrackerApiHandler create(final ApplicationDatabase applicationDatabase) {
        return new TrackerApiHandler(applicationDatabase);
    }

    @Override
    protected void handleGet(final HttpExchange exchange, final @Nullable String pathParameter) throws IOException, SQLException {
        sendJson(exchange, HttpStatus.OK, listTrackers());
    }

    @Override
    protected void handlePut(final HttpExchange exchange, final @Nullable String pathParameter) throws IOException, SQLException {
        final TrackerUpdate trackerUpdate = readJson(exchange, TrackerUpdate.class);
        final TrackerHandler trackerHandler = findHandler(trackerUpdate.name());

        final String username = trackerUpdate.username() == null ? "" : trackerUpdate.username().trim();
        // A null password keeps the stored password, and an empty password removes it
        final String password = trackerUpdate.password();

        if (trackerUpdate.enabled()) {
            if (username.isBlank()) {
                throw badRequest("A username is required to enable '%s'".formatted(trackerHandler.name()));
            }
            if (password == null ? !hasStoredPassword(trackerHandler.name()) : password.isEmpty()) {
                throw badRequest("A password is required to enable '%s'".formatted(trackerHandler.name()));
            }
        }

        applicationDatabase.saveCredential(trackerHandler.name(), username, password, trackerUpdate.enabled());
        sendJson(exchange, HttpStatus.OK, listTrackers());
    }

    @Override
    protected void handlePost(final HttpExchange exchange, final @Nullable String pathParameter) throws IOException, SQLException {
        if (!IMPORT_PATH.equals(pathParameter)) {
            throw new HttpRequestException(HttpStatus.NOT_FOUND, "Unknown action: '%s'".formatted(pathParameter));
        }

        final CsvImport csvImport = readJson(exchange, CsvImport.class);
        if (csvImport.csv() == null || csvImport.csv().isBlank()) {
            throw badRequest("The CSV file is empty");
        }

        final TrackerCsvReader.CsvContent csvContent;
        try {
            csvContent = TrackerCsvReader.read(csvImport.csv());
        } catch (final InvalidCsvInputException e) {
            throw new HttpRequestException(HttpStatus.BAD_REQUEST, e);
        }

        final Set<String> existingNames = applicationDatabase.findAllCredentials()
            .stream()
            .map(credential -> credential.name().toLowerCase(Locale.ROOT))
            .collect(Collectors.toSet());

        // Stores trackers using the name defined by their handler, rather than the casing used in the CSV file
        final List<StoredTrackerCredential> credentials = csvContent.credentials()
            .stream()
            .map(credential -> new StoredTrackerCredential(canonicalName(credential), credential.username(), credential.password(), true))
            .toList();
        applicationDatabase.saveCredentials(credentials);

        final int updated = (int) credentials.stream()
            .filter(credential -> existingNames.contains(credential.name().toLowerCase(Locale.ROOT)))
            .count();
        sendJson(exchange, HttpStatus.OK,
            new ImportResult(credentials.size() - updated, updated, List.copyOf(csvContent.unsupportedTrackers()), listTrackers()));
    }

    private static String canonicalName(final TrackerCredential credential) {
        return TrackerHandlerFactory.findMatchingHandler(credential.name())
            .map(TrackerHandler::name)
            .orElse(credential.name());
    }

    @Override
    protected void handleDelete(final HttpExchange exchange, final @Nullable String pathParameter) throws IOException, SQLException {
        if (pathParameter == null) {
            throw new HttpRequestException(HttpStatus.BAD_REQUEST, "Tracker name must be provided");
        }

        final TrackerHandler trackerHandler = findHandler(pathParameter);
        applicationDatabase.deleteCredential(trackerHandler.name());
        sendJson(exchange, HttpStatus.OK, listTrackers());
    }

    private List<TrackerView> listTrackers() throws SQLException {
        final Map<String, StoredTrackerCredential> credentialsByName = applicationDatabase.findAllCredentials()
            .stream()
            .collect(Collectors.toMap(credential -> credential.name().toLowerCase(Locale.ROOT), Function.identity(), (first, _) -> first));

        final Map<String, StoredTrackerResult> resultsByName = applicationDatabase.findAllResults()
            .stream()
            .collect(Collectors.toMap(result -> result.name().toLowerCase(Locale.ROOT), Function.identity(), (first, _) -> first));

        return TrackerHandlerFactory.allHandlers()
            .stream()
            .map(trackerHandler -> {
                final String key = trackerHandler.name().toLowerCase(Locale.ROOT);
                return TrackerView.of(trackerHandler, credentialsByName.get(key), resultsByName.get(key));
            })
            .toList();
    }

    private boolean hasStoredPassword(final String trackerName) throws SQLException {
        return applicationDatabase.findAllCredentials()
            .stream()
            .anyMatch(credential -> credential.name().equalsIgnoreCase(trackerName) && credential.hasPassword());
    }

    private static HttpRequestException badRequest(final String message) {
        return new HttpRequestException(HttpStatus.BAD_REQUEST, message);
    }

    private static TrackerHandler findHandler(final @Nullable String trackerName) {
        if (trackerName == null || trackerName.isBlank()) {
            throw new HttpRequestException(HttpStatus.BAD_REQUEST, "Tracker name must be provided");
        }

        return TrackerHandlerFactory.findMatchingHandler(trackerName)
            .orElseThrow(() -> new HttpRequestException(HttpStatus.NOT_FOUND, "Unsupported tracker: '%s'".formatted(trackerName)));
    }

    /**
     * The request body to save a tracker's credentials.
     *
     * @param name     the tracker name
     * @param username the username or email
     * @param password the new password, {@code null} to keep any existing password, or empty to remove it
     * @param enabled  whether the tracker should be executed
     */
    record TrackerUpdate(@Nullable String name, @Nullable String username, @Nullable String password, boolean enabled) {

    }

    /**
     * The request body to import tracker credentials.
     *
     * @param csv the content of the CSV file, with the header row {@code trackerName,username,password}
     */
    record CsvImport(@Nullable String csv) {

    }

    /**
     * The result of importing tracker credentials.
     *
     * @param added       the number of trackers added
     * @param updated     the number of trackers whose existing credentials were replaced
     * @param unsupported the names of the trackers in the CSV file that are not supported, and were not imported
     * @param trackers    every supported tracker, after the import
     */
    record ImportResult(int added, int updated, List<String> unsupported, List<TrackerView> trackers) {

    }

    /**
     * A supported tracker and its stored credentials, as returned to the web UI.
     *
     * @param name        the tracker name
     * @param type        the tracker type
     * @param adult       whether the tracker primarily hosts adult content
     * @param urls        the tracker URLs
     * @param username    the stored username or email (empty if not set)
     * @param hasPassword whether a password has been stored
     * @param enabled     whether the tracker is enabled for execution
     * @param lastRunAt   when the tracker last finished executing, as epoch milliseconds, or {@code null} if it has never been executed
     * @param lastRunOk   whether the most recent execution was successful, or {@code null} if it has never been executed
     */
    record TrackerView(String name, String type, boolean adult, List<String> urls, String username, boolean hasPassword, boolean enabled,
        @Nullable Long lastRunAt, @Nullable Boolean lastRunOk) {

        /**
         * Creates a {@link TrackerView} from a {@link TrackerHandler} and its stored credentials.
         *
         * @param trackerHandler the {@link TrackerHandler}
         * @param credential     the {@link StoredTrackerCredential}, or {@code null} if none are stored
         * @param result         the {@link StoredTrackerResult} of the most recent execution, or {@code null} if it has never been executed
         * @return the {@link TrackerView}
         */
        static TrackerView of(final TrackerHandler trackerHandler, final @Nullable StoredTrackerCredential credential,
            final @Nullable StoredTrackerResult result) {
            return new TrackerView(
                trackerHandler.name(),
                trackerHandler.type().name(),
                trackerHandler.adult(),
                List.of(trackerHandler.url()),
                credential == null ? "" : credential.username(),
                credential != null && credential.hasPassword(),
                credential != null && credential.enabled(),
                result == null ? null : result.finishedAt(),
                result == null ? null : result.successful()
            );
        }
    }
}
