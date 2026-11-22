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

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import net.zodac.tracker.web.storage.DatabaseBackup;

/**
 * The content of {@value BackupArchive#MANIFEST_ENTRY} in a backup archive: what the backup includes, and everything stored in the database except
 * the tracker credentials (which are in {@value BackupArchive#CREDENTIALS_ENTRY}, if included).
 *
 * <p>
 * The {@link #FORMAT_VERSION} must be increased whenever the content of a backup changes, and backups of every earlier version must still be
 * restored.
 *
 * @param format              always {@value #FORMAT}, to tell a backup apart from any other zip file
 * @param formatVersion       the version of the backup format, {@value #FORMAT_VERSION}
 * @param applicationVersion  the version of the application that created the backup
 * @param createdAt           when the backup was created, as epoch milliseconds
 * @param credentialsIncluded whether the tracker credentials are included, in {@value BackupArchive#CREDENTIALS_ENTRY}
 * @param trackers            whether each tracker in {@value BackupArchive#CREDENTIALS_ENTRY} is enabled (empty if the credentials are not
 *                            included), since a CSV import enables every tracker
 * @param screenshotsIncluded whether the screenshot files are included, under {@value BackupArchive#SCREENSHOTS_DIRECTORY}
 * @param screenshotFiles     the name of the file of each included screenshot under {@value BackupArchive#SCREENSHOTS_DIRECTORY} (relative to the
 *                            screenshot folder), keyed by the path stored for the screenshot (empty if the screenshots are not included)
 * @param database            everything stored in the database except the tracker credentials
 */
public record BackupManifest(
    String format,
    int formatVersion,
    String applicationVersion,
    long createdAt,
    boolean credentialsIncluded,
    List<TrackerState> trackers,
    boolean screenshotsIncluded,
    Map<String, String> screenshotFiles,
    DatabaseBackup database
) {

    /**
     * The value of {@link #format()} of every backup.
     */
    public static final String FORMAT = "tracker-profiles-backup";

    /**
     * The current version of the backup format.
     */
    public static final int FORMAT_VERSION = 1;

    private static final String SCREENSHOT_EXTENSION = ".png";

    /**
     * Checks that the manifest (read from a file uploaded by the user) is complete and consistent, so the backup can be restored. Values may be
     * missing (null), since the manifest is read from JSON.
     *
     * @throws IllegalArgumentException thrown if the file is not a backup, was created by a newer version, or any of its values is missing,
     *                                  duplicated or invalid
     */
    public void validate() {
        if (!FORMAT.equals(format)) {
            throw new IllegalArgumentException("The file is not a backup of tracker-profiles");
        }
        if (formatVersion > FORMAT_VERSION) {
            throw new IllegalArgumentException("The backup was created by a newer version of tracker-profiles (%s)".formatted(applicationVersion));
        }
        if (formatVersion < 1) {
            throw invalid("its format version is missing");
        }
        if (database == null) {
            throw invalid("the database is missing");
        }
        database.validate();

        if (trackers == null || trackers.stream().anyMatch(tracker -> tracker == null || tracker.name() == null)) {
            throw invalid("the trackers are missing or incomplete");
        }
        if (screenshotFiles == null
            || screenshotFiles.entrySet().stream().anyMatch(file -> file.getKey() == null || !isValidFileName(file.getValue()))) {
            throw invalid("the screenshot files are missing or invalid");
        }
    }

    // A file under the screenshot folder: a relative path with no '..', to a PNG
    private static boolean isValidFileName(final String fileName) {
        if (fileName == null || fileName.isBlank() || !fileName.toLowerCase(Locale.ROOT).endsWith(SCREENSHOT_EXTENSION)) {
            return false;
        }
        final Path path = Path.of(fileName).normalize();
        return !path.isAbsolute() && Objects.equals(path.toString().replace('\\', '/'), fileName) && !fileName.startsWith("..");
    }

    private static IllegalArgumentException invalid(final String reason) {
        return new IllegalArgumentException("The backup is invalid: %s".formatted(reason));
    }

    /**
     * Whether a tracker whose credentials are included in a backup is enabled.
     *
     * @param name    the tracker name
     * @param enabled whether the tracker is enabled for execution
     */
    public record TrackerState(String name, boolean enabled) {

    }
}
