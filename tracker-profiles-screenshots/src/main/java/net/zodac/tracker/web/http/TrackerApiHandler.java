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

import com.google.gson.annotations.SerializedName;
import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;
import net.zodac.tracker.framework.TrackerCredential;
import net.zodac.tracker.framework.TrackerCsvReader;
import net.zodac.tracker.framework.TrackerCsvWriter;
import net.zodac.tracker.framework.TrackerHandlerFactory;
import net.zodac.tracker.framework.annotation.TrackerHandler;
import net.zodac.tracker.framework.exception.InvalidCsvInputException;
import net.zodac.tracker.web.RunManager;
import net.zodac.tracker.web.RunStatus;
import net.zodac.tracker.web.screenshot.ScreenshotFile;
import net.zodac.tracker.web.storage.ApplicationDatabase;
import net.zodac.tracker.web.storage.StoredTrackerCredential;
import net.zodac.tracker.web.storage.StoredTrackerResult;
import net.zodac.tracker.web.storage.TrackerCredentialStatus;
import org.jspecify.annotations.Nullable;

/**
 * Handles requests to list every tracker with its stored credentials, most recent result and its latest screenshots
 * ({@code GET}), save the credentials for a
 * tracker ({@code PUT}), import credentials from a CSV file ({@code POST /api/trackers/import}), export the credentials of every supported tracker
 * with a username and password as a CSV file ({@code GET /api/trackers/export}), and remove the stored credentials for a tracker
 * ({@code DELETE /api/trackers/{name}}).
 *
 * <p>
 * A legacy tracker (one with anything stored, but whose handler has since been removed) is listed after the supported trackers, so the screenshots
 * of its latest execution can still be retrieved. It is never removed automatically: removing it ({@code DELETE}, or the {@code remove} bulk
 * action) clears everything stored for it (its credentials, result and screenshots, though the screenshot files are kept), and it cannot be changed
 * otherwise. Trackers are identified only by name (ignoring case), so a renamed tracker is the same as a removed one: its old name is a legacy
 * tracker, and nothing stored for it is carried over to its new name.
 *
 * <p>
 * Passwords are never returned to the client (only whether a password has been saved), except in the CSV file downloaded from
 * {@code GET /api/trackers/export}, so that it can be imported again.
 */
public final class TrackerApiHandler extends ApiHandler {

    /**
     * The context path for this handler.
     */
    public static final String CONTEXT_PATH = "/api/trackers";

    private static final String IMPORT_PATH = "import";
    private static final String BULK_PATH = "bulk";
    private static final String EXPORT_PATH = "export";
    private static final String EXPORT_FILE_NAME = "trackers.csv";

    private final ApplicationDatabase applicationDatabase;
    private final RunManager runManager;

    private TrackerApiHandler(final ApplicationDatabase applicationDatabase, final RunManager runManager) {
        super(CONTEXT_PATH);
        this.applicationDatabase = applicationDatabase;
        this.runManager = runManager;
    }

    /**
     * Creates a {@link TrackerApiHandler}.
     *
     * @param applicationDatabase the {@link ApplicationDatabase} storing tracker credentials
     * @param runManager          the {@link RunManager} holding the latest screenshots of each tracker
     * @return the {@link TrackerApiHandler}
     */
    public static TrackerApiHandler create(final ApplicationDatabase applicationDatabase, final RunManager runManager) {
        return new TrackerApiHandler(applicationDatabase, runManager);
    }

    @Override
    protected void handleGet(final HttpExchange exchange, final @Nullable String pathParameter) throws IOException, SQLException {
        if (EXPORT_PATH.equals(pathParameter)) {
            sendExport(exchange);
            return;
        }
        sendJson(exchange, HttpStatus.OK, listTrackers());
    }

    // Every supported tracker with a username and password (enabled or not), which is only the header row if there are none
    private void sendExport(final HttpExchange exchange) throws IOException, SQLException {
        final List<TrackerCredential> credentials = applicationDatabase.credentials().findAll()
            .stream()
            .filter(credential -> !credential.username().isBlank() && credential.hasPassword())
            .filter(credential -> TrackerHandlerFactory.findMatchingHandler(credential.name()).isPresent())
            .map(StoredTrackerCredential::toTrackerCredential)
            .toList();

        final byte[] csv = TrackerCsvWriter.write(credentials).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/csv; charset=utf-8");
        exchange.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"%s\"".formatted(EXPORT_FILE_NAME));
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.sendResponseHeaders(HttpStatus.OK.code(), csv.length);
        try (final OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(csv);
        }
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
            if (password == null ? !applicationDatabase.credentials().hasPassword(trackerHandler.name()) : password.isEmpty()) {
                throw badRequest("A password is required to enable '%s'".formatted(trackerHandler.name()));
            }
        }

        applicationDatabase.credentials().save(trackerHandler.name(), username, password, trackerUpdate.enabled());
        sendJson(exchange, HttpStatus.OK, listTrackers());
    }

    @Override
    protected void handlePost(final HttpExchange exchange, final @Nullable String pathParameter) throws IOException, SQLException {
        if (BULK_PATH.equals(pathParameter)) {
            handleBulkChange(exchange);
            return;
        }

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

        final Set<String> existingNames = applicationDatabase.credentials().findAllStatuses()
            .stream()
            .map(credential -> credential.name().toLowerCase(Locale.ROOT))
            .collect(Collectors.toSet());

        // Stores trackers using the name defined by their handler, rather than the casing used in the CSV file
        final List<StoredTrackerCredential> credentials = csvContent.credentials()
            .stream()
            .map(credential -> new StoredTrackerCredential(canonicalName(credential), credential.username(), credential.password(), true))
            .toList();
        applicationDatabase.credentials().saveAll(credentials);

        final int updated = (int) credentials.stream()
            .filter(credential -> existingNames.contains(credential.name().toLowerCase(Locale.ROOT)))
            .count();
        sendJson(exchange, HttpStatus.OK,
            new ImportResult(credentials.size() - updated, updated, List.copyOf(csvContent.unsupportedTrackers()), listTrackers()));
    }

    // Enables, disables or removes several trackers in a single transaction, with a single response
    private void handleBulkChange(final HttpExchange exchange) throws IOException, SQLException {
        final BulkChange bulkChange = readJson(exchange, BulkChange.class);
        if (bulkChange.action() == null) {
            throw badRequest("An action is required (one of: enable, disable, remove)");
        }

        final List<String> requestedNames = bulkChange.names() == null ? List.of() : bulkChange.names();
        // Unsupported trackers are skipped, using the name defined by the handler of the others
        final List<String> names = requestedNames
            .stream()
            .map(TrackerHandlerFactory::findMatchingHandler)
            .flatMap(Optional::stream)
            .map(TrackerHandler::name)
            .distinct()
            .toList();

        final List<String> changed = switch (bulkChange.action()) {
            case ENABLE -> applicationDatabase.credentials().changeEnabled(names, true);
            case DISABLE -> applicationDatabase.credentials().changeEnabled(names, false);
            case REMOVE -> {
                final List<String> removed = new ArrayList<>(applicationDatabase.credentials().deleteAll(names));
                removed.addAll(removeLegacyTrackers(requestedNames));
                yield removed;
            }
        };
        sendJson(exchange, HttpStatus.OK, new BulkResult(changed, listTrackers()));
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

        final Optional<TrackerHandler> trackerHandler = TrackerHandlerFactory.findMatchingHandler(pathParameter);
        if (trackerHandler.isPresent()) {
            applicationDatabase.credentials().delete(trackerHandler.get().name());
        } else if (removeLegacyTrackers(List.of(pathParameter)).isEmpty()) {
            throw new HttpRequestException(HttpStatus.NOT_FOUND, "Unsupported tracker: '%s'".formatted(pathParameter));
        }
        sendJson(exchange, HttpStatus.OK, listTrackers());
    }

    // Clears everything stored for the requested trackers that are legacy trackers (never a supported tracker), returning their names
    private List<String> removeLegacyTrackers(final List<String> requestedNames) throws SQLException {
        final Set<String> legacyNames = legacyTrackerNames();
        final List<String> names = requestedNames
            .stream()
            .filter(legacyNames::contains)
            .distinct()
            .toList();
        if (names.isEmpty()) {
            return List.of();
        }

        final List<String> removed = applicationDatabase.removeTrackers(names);
        runManager.forgetTrackers(removed);
        return removed;
    }

    private Set<String> legacyTrackerNames() throws SQLException {
        return legacyTrackerNames(
            applicationDatabase.credentials().findAllStatuses().stream().map(TrackerCredentialStatus::name).toList(),
            applicationDatabase.results().findAll().stream().map(StoredTrackerResult::name).toList(),
            runManager.latestScreenshotsByTracker().keySet()
        );
    }

    // The names of the trackers with anything stored but no handler, ordered by name (case-insensitive)
    private static Set<String> legacyTrackerNames(final Collection<String> credentialNames, final Collection<String> resultNames,
        final Collection<String> screenshotNames) {
        final Set<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        names.addAll(credentialNames);
        names.addAll(resultNames);
        names.addAll(screenshotNames);
        names.removeIf(name -> TrackerHandlerFactory.findMatchingHandler(name).isPresent());
        return names;
    }

    private List<TrackerView> listTrackers() throws SQLException {
        final List<TrackerCredentialStatus> credentials = applicationDatabase.credentials().findAllStatuses();
        final Map<String, TrackerCredentialStatus> credentialsByName = credentials
            .stream()
            .collect(Collectors.toMap(credential -> credential.name().toLowerCase(Locale.ROOT), Function.identity(), (first, _) -> first));

        final List<StoredTrackerResult> results = applicationDatabase.results().findAll();
        final Map<String, StoredTrackerResult> resultsByName = results
            .stream()
            .collect(Collectors.toMap(result -> result.name().toLowerCase(Locale.ROOT), Function.identity(), (first, _) -> first));

        // Keyed by tracker name, ignoring case
        final Map<String, List<ScreenshotFile>> screenshotsByName = runManager.latestScreenshotsByTracker();
        final Map<String, Long> estimatedDurations = runManager.estimatedDurations();

        final List<TrackerView> trackerViews = new ArrayList<>();
        for (final TrackerHandler trackerHandler : TrackerHandlerFactory.allHandlers()) {
            final String key = trackerHandler.name().toLowerCase(Locale.ROOT);
            trackerViews.add(TrackerView.of(trackerHandler, credentialsByName.get(key), resultsByName.get(key),
                screenshotsByName.getOrDefault(trackerHandler.name(), List.of()), estimatedDurations.get(trackerHandler.name())));
        }

        // Listed by the name they were stored with, after the supported trackers
        final Set<String> legacyNames = legacyTrackerNames(credentials.stream().map(TrackerCredentialStatus::name).toList(),
            results.stream().map(StoredTrackerResult::name).toList(), screenshotsByName.keySet());
        for (final String legacyName : legacyNames) {
            final String key = legacyName.toLowerCase(Locale.ROOT);
            trackerViews.add(TrackerView.ofLegacy(storedName(legacyName, credentialsByName.get(key), resultsByName.get(key)),
                credentialsByName.get(key), resultsByName.get(key), screenshotsByName.getOrDefault(legacyName, List.of()),
                estimatedDurations.get(legacyName)));
        }
        return trackerViews;
    }

    private static String storedName(final String name, final @Nullable TrackerCredentialStatus credential,
        final @Nullable StoredTrackerResult result) {
        if (credential != null) {
            return credential.name();
        }
        return result == null ? name : result.name();
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
     * The request body to change several trackers at once.
     *
     * @param action the {@link BulkAction} to apply
     * @param names  the names of the trackers to change
     */
    record BulkChange(@Nullable BulkAction action, @Nullable List<String> names) {

    }

    /**
     * A change applied to several trackers at once.
     */
    enum BulkAction {

        /**
         * Enables the trackers that have a username and password.
         */
        @SerializedName("enable")
        ENABLE,

        /**
         * Disables the trackers.
         */
        @SerializedName("disable")
        DISABLE,

        /**
         * Removes the stored credentials of the trackers.
         */
        @SerializedName("remove")
        REMOVE
    }

    /**
     * The result of changing several trackers at once.
     *
     * @param changed  the names of the trackers that were changed (the others were skipped)
     * @param trackers every tracker, after the change
     */
    record BulkResult(List<String> changed, List<TrackerView> trackers) {

    }

    /**
     * The result of importing tracker credentials.
     *
     * @param added       the number of trackers added
     * @param updated     the number of trackers whose existing credentials were replaced
     * @param unsupported the names of the trackers in the CSV file that are not supported, and were not imported
     * @param trackers    every tracker, after the import
     */
    record ImportResult(int added, int updated, List<String> unsupported, List<TrackerView> trackers) {

    }

    /**
     * A tracker and its stored credentials, as returned to the web UI.
     *
     * @param name              the tracker name
     * @param type              the tracker type, or {@code null} for a legacy tracker
     * @param urls              the tracker URLs (empty for a legacy tracker)
     * @param username          the stored username or email (empty if not set)
     * @param hasPassword       whether a password has been stored
     * @param enabled           whether the tracker is enabled for execution
     * @param lastRunAt         when the tracker last finished executing, as epoch milliseconds, or {@code null} if it has never been executed
     * @param lastRunOk         whether the most recent execution was successful, or {@code null} if it has never been executed
     * @param estimatedDuration the estimated duration of the tracker in milliseconds (the average of its most recent successful executions,
     *                          excluding any time spent waiting for a user input), or {@code null} if it has not succeeded often enough
     * @param screenshots       the screenshots of the latest execution (the profile screenshots if it succeeded, or the failure screenshot if it
     *                          failed) that still exist, retrieved with
     *                          {@code /api/screenshots?tracker={name}&id={id}&latest=true}
     * @param legacy            whether the tracker is no longer supported (its handler has been removed), so it is only kept until removed by
     *                          the user
     */
    record TrackerView(String name, @Nullable String type, List<String> urls, String username, boolean hasPassword, boolean enabled,
        @Nullable Long lastRunAt, @Nullable Boolean lastRunOk, @Nullable Long estimatedDuration, List<RunStatus.Screenshot> screenshots,
        boolean legacy) {

        /**
         * Creates a {@link TrackerView} from a {@link TrackerHandler} and its stored credentials.
         *
         * @param trackerHandler    the {@link TrackerHandler}
         * @param credential        the {@link StoredTrackerCredential}, or {@code null} if none are stored
         * @param result            the {@link StoredTrackerResult} of the most recent execution, or {@code null} if it has never been executed
         * @param screenshots       the latest {@link ScreenshotFile}s of the tracker
         * @param estimatedDuration the estimated duration of the tracker in milliseconds, or {@code null} if it has none
         * @return the {@link TrackerView}
         */
        static TrackerView of(final TrackerHandler trackerHandler, final @Nullable TrackerCredentialStatus credential,
            final @Nullable StoredTrackerResult result, final List<ScreenshotFile> screenshots, final @Nullable Long estimatedDuration) {
            return new TrackerView(
                trackerHandler.name(),
                trackerHandler.type().name(),
                List.of(trackerHandler.url()),
                credential == null ? "" : credential.username(),
                credential != null && credential.hasPassword(),
                credential != null && credential.enabled(),
                result == null ? null : result.finishedAt(),
                result == null ? null : result.successful(),
                estimatedDuration,
                screenshots.stream().map(RunStatus.Screenshot::of).toList(),
                false
            );
        }

        /**
         * Creates a {@link TrackerView} for a legacy tracker, whose handler has been removed, from what is still stored for it.
         *
         * @param name              the tracker name
         * @param credential        the {@link TrackerCredentialStatus}, or {@code null} if none are stored
         * @param result            the {@link StoredTrackerResult} of the most recent execution, or {@code null} if it has never been executed
         * @param screenshots       the latest {@link ScreenshotFile}s of the tracker
         * @param estimatedDuration the estimated duration of the tracker in milliseconds, or {@code null} if it has none
         * @return the {@link TrackerView}
         */
        static TrackerView ofLegacy(final String name, final @Nullable TrackerCredentialStatus credential, final @Nullable StoredTrackerResult result,
            final List<ScreenshotFile> screenshots, final @Nullable Long estimatedDuration) {
            return new TrackerView(
                name,
                null,
                List.of(),
                credential == null ? "" : credential.username(),
                credential != null && credential.hasPassword(),
                credential != null && credential.enabled(),
                result == null ? null : result.finishedAt(),
                result == null ? null : result.successful(),
                estimatedDuration,
                screenshots.stream().map(RunStatus.Screenshot::of).toList(),
                true
            );
        }
    }
}
