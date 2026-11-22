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

package net.zodac.tracker.web.backup;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import net.zodac.tracker.framework.ApplicationVersion;
import net.zodac.tracker.framework.TrackerCredential;
import net.zodac.tracker.framework.TrackerCsvReader;
import net.zodac.tracker.framework.TrackerCsvWriter;
import net.zodac.tracker.framework.TrackerHandlerFactory;
import net.zodac.tracker.framework.annotation.TrackerHandler;
import net.zodac.tracker.framework.config.Configuration;
import net.zodac.tracker.framework.exception.InvalidCsvInputException;
import net.zodac.tracker.web.RunManager;
import net.zodac.tracker.web.screenshot.ScreenshotDownload;
import net.zodac.tracker.web.screenshot.ScreenshotFile;
import net.zodac.tracker.web.storage.ApplicationDatabase;
import net.zodac.tracker.web.storage.DatabaseBackup;
import net.zodac.tracker.web.storage.StoredTrackerCredential;
import net.zodac.tracker.web.storage.StoredTrackerScreenshot;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jspecify.annotations.Nullable;

/**
 * Creates and restores backups (see {@link BackupArchive}), such as to move the application to another machine. A backup always has everything
 * stored in the database except the tracker credentials, which are included (as by the CSV export of the Trackers tab) only if requested, as are
 * the screenshot files (the latest screenshots of every tracker, as downloaded by "All" on the Trackers tab).
 *
 * <p>
 * A restore merges the backup with what is already stored (see {@link ApplicationDatabase#restore(DatabaseBackup, List)}), restoring the
 * credentials and the screenshot files if the backup includes them. Screenshot files are restored into the current screenshot folder, at the same
 * path relative to it as when they were backed up.
 */
public final class BackupService {

    private static final Logger LOGGER = LogManager.getLogger();
    private static final DateTimeFormatter FILE_NAME_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH:mm:ss", Locale.ROOT);

    private final ApplicationDatabase applicationDatabase;
    private final RunManager runManager;

    private BackupService(final ApplicationDatabase applicationDatabase, final RunManager runManager) {
        this.applicationDatabase = applicationDatabase;
        this.runManager = runManager;
    }

    /**
     * Creates a {@link BackupService}.
     *
     * @param applicationDatabase the {@link ApplicationDatabase} to back up
     * @param runManager          the {@link RunManager} holding the latest screenshots, which restores a backup
     * @return the {@link BackupService}
     */
    public static BackupService create(final ApplicationDatabase applicationDatabase, final RunManager runManager) {
        return new BackupService(applicationDatabase, runManager);
    }

    /**
     * The name of the file of a backup, such as {@code tracker-profiles_backup_v1.2.3_2026-01-31_12:34:56.zip}.
     *
     * @param createdAt when the backup was created, as epoch milliseconds
     * @param timezone  the {@link ZoneId} in which the time is formatted
     * @param version   the version of the application that created the backup
     * @return the file name
     */
    public static String fileName(final long createdAt, final ZoneId timezone, final String version) {
        final String date = FILE_NAME_DATE_FORMAT.format(Instant.ofEpochMilli(createdAt).atZone(timezone));
        return "tracker-profiles_backup_v%s_%s.zip".formatted(version, date);
    }

    /**
     * Prepares a backup, reading everything it includes from the database.
     *
     * @param includeCredentials whether to include the tracker credentials (with their passwords in plain text)
     * @param includeScreenshots whether to include the latest screenshot files of every tracker
     * @return the {@link Backup}, to be written with {@link Backup#write(OutputStream)}
     * @throws SQLException thrown if the database cannot be read
     */
    public Backup prepare(final boolean includeCredentials, final boolean includeScreenshots) throws SQLException {
        final DatabaseBackup database = applicationDatabase.backup();

        String credentials = null;
        final List<BackupManifest.TrackerState> trackers = new ArrayList<>();
        if (includeCredentials) {
            final List<StoredTrackerCredential> exported = applicationDatabase.credentials().findAll()
                .stream()
                .filter(StoredTrackerCredential::isExported)
                .toList();
            credentials = TrackerCsvWriter.write(exported.stream().map(StoredTrackerCredential::toTrackerCredential).toList());
            exported.forEach(credential -> trackers.add(new BackupManifest.TrackerState(credential.name(), credential.enabled())));
        }

        // Named by their path relative to the screenshot folder, so they are restored in the same place (in a new screenshot folder)
        final Map<String, String> fileNamesByPath = new LinkedHashMap<>();
        final Map<String, Path> files = new LinkedHashMap<>();
        if (includeScreenshots) {
            final Path folder = screenshotFolder();
            for (final ScreenshotFile screenshot : runManager.latestScreenshots(ScreenshotDownload.ALL)) {
                final Path path = screenshot.path().toAbsolutePath().normalize();
                final String fileName = uniqueFileName(folder, path, files.keySet());
                fileNamesByPath.put(path.toString(), fileName);
                files.put(fileName, path);
            }
        }

        final String version = ApplicationVersion.current();
        final BackupManifest manifest = new BackupManifest(BackupManifest.FORMAT, BackupManifest.FORMAT_VERSION, version, System.currentTimeMillis(),
            includeCredentials, List.copyOf(trackers), includeScreenshots, fileNamesByPath, database);
        return new Backup(manifest, credentials, files, fileName(manifest.createdAt(), Configuration.get().timezone(), version));
    }

    /**
     * Restores a backup archive (see {@link BackupArchive}), unless an execution is in progress: its screenshot files (if included) are restored
     * as the archive is read, then everything else is restored in a single transaction.
     *
     * @param inputStream the {@link InputStream} of the backup archive
     * @return the {@link RestoreResult}
     * @throws IOException              thrown if the archive cannot be read, or a screenshot cannot be written
     * @throws IllegalArgumentException thrown if the file is not a valid backup
     * @throws IllegalStateException    thrown if an execution is in progress
     * @throws SQLException             thrown if the database cannot be updated, in which case nothing is changed in it
     */
    public RestoreResult restore(final InputStream inputStream) throws IOException, SQLException {
        runManager.checkCanRestoreBackup();
        final BackupArchive.Content content = BackupArchive.read(inputStream, screenshotFolder());
        final BackupManifest manifest = content.manifest();

        final List<StoredTrackerCredential> credentials = new ArrayList<>();
        final List<String> unsupportedTrackers = new ArrayList<>();
        if (content.credentials() != null) {
            readCredentials(manifest, content.credentials(), credentials, unsupportedTrackers);
        }

        // A screenshot whose file was restored is stored with its new path
        final List<StoredTrackerScreenshot> screenshots = manifest.database().screenshots()
            .stream()
            .map(screenshot -> {
                final Path restored = content.restoredFiles().get(screenshot.path());
                if (restored == null) {
                    return screenshot;
                }
                return new StoredTrackerScreenshot(screenshot.name(), screenshot.id(), screenshot.position(), restored.toString(),
                    screenshot.savedAt());
            })
            .toList();

        final Map<String, String> restoredSettings = runManager.restoreBackup(manifest.database().withScreenshots(screenshots), credentials);
        final List<String> ignoredSettings = manifest.database().settings()
            .keySet()
            .stream()
            .filter(name -> !restoredSettings.containsKey(name))
            .sorted()
            .toList();

        LOGGER.info("Restored a backup created at {} by version {}: {} settings, {} trackers, {} screenshot files",
            Instant.ofEpochMilli(manifest.createdAt()), manifest.applicationVersion(), restoredSettings.size(), credentials.size(),
            content.restoredFiles().size());
        return new RestoreResult(manifest.createdAt(), manifest.applicationVersion(), restoredSettings.size(), ignoredSettings,
            manifest.credentialsIncluded(), credentials.size(), List.copyOf(unsupportedTrackers), manifest.screenshotsIncluded(),
            content.restoredFiles().size());
    }

    // Read as a CSV import does (with the name defined by each tracker's handler), keeping whether each tracker was enabled
    private static void readCredentials(final BackupManifest manifest, final String csv, final List<StoredTrackerCredential> credentials,
        final List<String> unsupportedTrackers) {
        final TrackerCsvReader.CsvContent csvContent;
        try {
            csvContent = TrackerCsvReader.read(csv);
        } catch (final InvalidCsvInputException e) {
            throw new IllegalArgumentException("The backup is invalid: %s".formatted(e.getMessage()), e);
        }

        final Map<String, Boolean> enabled = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        manifest.trackers().forEach(tracker -> enabled.put(tracker.name(), tracker.enabled()));
        for (final TrackerCredential credential : csvContent.credentials()) {
            final String name = TrackerHandlerFactory.findMatchingHandler(credential.name())
                .map(TrackerHandler::name)
                .orElse(credential.name());
            credentials.add(new StoredTrackerCredential(name, credential.username(), credential.password(), enabled.getOrDefault(name, true)));
        }
        unsupportedTrackers.addAll(csvContent.unsupportedTrackers());
    }

    // The parent of the dated output directory
    private static Path screenshotFolder() {
        final Path outputDirectory = Configuration.get().outputDirectory().toAbsolutePath().normalize();
        final Path parent = outputDirectory.getParent();
        return parent == null ? outputDirectory : parent;
    }

    // The path relative to the screenshot folder, or only the file name for a screenshot outside it, with a number added if it is already used
    private static String uniqueFileName(final Path folder, final Path path, final Set<String> usedFileNames) {
        final String fileName = path.startsWith(folder) && !path.equals(folder)
            ? folder.relativize(path).toString().replace('\\', '/')
            : String.valueOf(path.getFileName());
        if (!usedFileNames.contains(fileName)) {
            return fileName;
        }

        final int extensionIndex = fileName.lastIndexOf('.');
        final String baseName = extensionIndex > 0 ? fileName.substring(0, extensionIndex) : fileName;
        final String extension = extensionIndex > 0 ? fileName.substring(extensionIndex) : "";
        int index = 1;
        while (usedFileNames.contains("%s_%d%s".formatted(baseName, index, extension))) {
            index++;
        }
        return "%s_%d%s".formatted(baseName, index, extension);
    }

    /**
     * A backup ready to be written.
     *
     * @param manifest    the {@link BackupManifest}
     * @param credentials the tracker credentials in the format of the CSV export, or {@code null} if they are not included
     * @param files       the screenshot files to include, keyed by their name in the archive
     * @param fileName    the name of the backup file
     */
    public record Backup(BackupManifest manifest, @Nullable String credentials, Map<String, Path> files, String fileName) {

        /**
         * Writes the backup archive.
         *
         * @param outputStream the {@link OutputStream} to write to (not closed)
         * @throws IOException thrown if the archive cannot be written, or a screenshot cannot be read
         */
        public void write(final OutputStream outputStream) throws IOException {
            BackupArchive.write(outputStream, manifest, credentials, files);
        }
    }

    /**
     * The result of restoring a backup.
     *
     * @param createdAt           when the backup was created, as epoch milliseconds
     * @param applicationVersion  the version of the application that created the backup
     * @param settings            the number of settings restored
     * @param ignoredSettings     the names of the settings in the backup that were not restored, since they no longer exist or can only be set as
     *                            environment variables
     * @param credentialsIncluded whether the backup includes the tracker credentials
     * @param trackers            the number of trackers whose credentials were restored
     * @param unsupportedTrackers the names of the trackers in the backup that are no longer supported, whose credentials were not restored
     * @param screenshotsIncluded whether the backup includes the screenshot files
     * @param screenshots         the number of screenshot files restored
     */
    public record RestoreResult(
        long createdAt,
        String applicationVersion,
        int settings,
        List<String> ignoredSettings,
        boolean credentialsIncluded,
        int trackers,
        List<String> unsupportedTrackers,
        boolean screenshotsIncluded,
        int screenshots
    ) {

    }
}
