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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import net.zodac.tracker.app.ScheduledTracker;
import net.zodac.tracker.framework.TrackerCredential;
import net.zodac.tracker.framework.TrackerType;
import net.zodac.tracker.framework.config.Configuration;
import net.zodac.tracker.framework.redactor.RedactionType;
import net.zodac.tracker.web.RunManager;
import net.zodac.tracker.web.log.WebLogAppender;
import net.zodac.tracker.web.screenshot.ScreenshotFile;
import net.zodac.tracker.web.storage.ApplicationDatabase;
import net.zodac.tracker.web.storage.StoredTrackerCredential;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unit tests for {@link BackupService}.
 */
class BackupServiceTest {

    @TempDir
    private Path tempDirectory;

    @BeforeEach
    void setUp() {
        Configuration.reload(Map.of());
    }

    @AfterEach
    void tearDown() {
        Configuration.reload(Map.of());
    }

    @Test
    void givenBackupWithCredentialsAndScreenshots_whenRestoredOnAnotherMachine_thenEverythingIsRestored() throws IOException, SQLException {
        final Path sourceFolder = tempDirectory.resolve("source");
        final byte[] backup;
        Configuration.reload(Map.of("OUTPUT_DIRECTORY_PARENT_PATH", sourceFolder.toString()));
        try (final ApplicationDatabase source = ApplicationDatabase.open(tempDirectory.resolve("source.db"), "source-secret-key-value")) {
            source.credentials().save("AnimeZ", "user", "pass,with\"quotes", false);
            source.credentials().save("Aither", "other", "password", true);
            // Not exported, without a password
            source.credentials().save("Beta", "user", "", false);
            final RunManager runManager = RunManager.create(source, WebLogAppender.install());
            completeTracker(runManager, "AnimeZ", createScreenshot(sourceFolder.resolve("2026-01-01").resolve("AnimeZ_box.png"), "box"));
            completeTracker(runManager, "Aither", createScreenshot(tempDirectory.resolve("elsewhere").resolve("Aither_box.png"), "other"));

            backup = write(BackupService.create(source, runManager).prepare(true, true));
        }

        final Path targetFolder = tempDirectory.resolve("target");
        Configuration.reload(Map.of("OUTPUT_DIRECTORY_PARENT_PATH", targetFolder.toString()));
        try (final ApplicationDatabase target = ApplicationDatabase.open(tempDirectory.resolve("target.db"), "target-secret-key-value")) {
            target.credentials().save("Kept", "user", "password", true);
            final RunManager runManager = RunManager.create(target, WebLogAppender.install());

            final BackupService.RestoreResult result = BackupService.create(target, runManager).restore(new ByteArrayInputStream(backup));

            assertThat(result)
                .returns(true, BackupService.RestoreResult::credentialsIncluded)
                .returns(2, BackupService.RestoreResult::trackers)
                .returns(true, BackupService.RestoreResult::screenshotsIncluded)
                .returns(2, BackupService.RestoreResult::screenshots);
            assertThat(target.credentials().findAll())
                .containsExactly(new StoredTrackerCredential("Aither", "other", "password", true),
                    new StoredTrackerCredential("AnimeZ", "user", "pass,with\"quotes", false),
                    new StoredTrackerCredential("Kept", "user", "password", true));
            assertThat(runManager.latestScreenshots("AnimeZ"))
                .extracting(ScreenshotFile::path)
                .containsExactly(targetFolder.resolve("2026-01-01").resolve("AnimeZ_box.png").toAbsolutePath());
            assertThat(runManager.latestScreenshots("Aither"))
                .extracting(ScreenshotFile::path)
                .containsExactly(targetFolder.resolve("Aither_box.png").toAbsolutePath());
            assertThat(targetFolder.resolve("2026-01-01").resolve("AnimeZ_box.png"))
                .hasContent("box");
        }
    }

    @Test
    void givenBackupWithoutCredentialsOrScreenshots_whenRestored_thenStoredCredentialsAreKept() throws IOException, SQLException {
        Configuration.reload(Map.of("OUTPUT_DIRECTORY_PARENT_PATH", tempDirectory.resolve("screenshots").toString()));
        try (final ApplicationDatabase database = ApplicationDatabase.open(tempDirectory.resolve("test.db"), "test-secret-key-value")) {
            final RunManager runManager = RunManager.create(database, WebLogAppender.install());
            final BackupService backupService = BackupService.create(database, runManager);
            final byte[] backup = write(backupService.prepare(false, false));
            database.credentials().save("AnimeZ", "user", "password", true);

            final BackupService.RestoreResult result = backupService.restore(new ByteArrayInputStream(backup));

            assertThat(result)
                .returns(false, BackupService.RestoreResult::credentialsIncluded)
                .returns(false, BackupService.RestoreResult::screenshotsIncluded);
            assertThat(database.credentials().findAll())
                .containsExactly(new StoredTrackerCredential("AnimeZ", "user", "password", true));
        }
    }

    @Test
    void givenFileThatIsNotBackup_whenRestored_thenExceptionIsThrown() throws IOException, SQLException {
        try (final ApplicationDatabase database = ApplicationDatabase.open(tempDirectory.resolve("test.db"), "test-secret-key-value")) {
            final BackupService backupService = BackupService.create(database, RunManager.create(database, WebLogAppender.install()));
            final ByteArrayOutputStream otherZip = new ByteArrayOutputStream();
            try (final ZipOutputStream zipOutputStream = new ZipOutputStream(otherZip)) {
                zipOutputStream.putNextEntry(new ZipEntry("other.txt"));
                zipOutputStream.write("content".getBytes(StandardCharsets.UTF_8));
            }

            assertThatThrownBy(() -> backupService.restore(new ByteArrayInputStream("not a zip file".getBytes(StandardCharsets.UTF_8))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("The file is not a backup of tracker-profiles");
            assertThatThrownBy(() -> backupService.restore(new ByteArrayInputStream(otherZip.toByteArray())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("The file is not a backup of tracker-profiles");
        }
    }

    @Test
    void givenScreenshotFileOutsideScreenshotFolder_whenManifestValidated_thenExceptionIsThrown() throws IOException, SQLException {
        try (final ApplicationDatabase database = ApplicationDatabase.open(tempDirectory.resolve("test.db"), "test-secret-key-value")) {
            final BackupManifest manifest = new BackupManifest(BackupManifest.FORMAT, BackupManifest.FORMAT_VERSION, "1.0.0", 0L, false, List.of(),
                true, Map.of("/screenshots/Alpha.png", "../Alpha.png"), database.backup());

            assertThatThrownBy(manifest::validate)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("screenshot files are missing or invalid");
        }
    }

    private static byte[] write(final BackupService.Backup backup) throws IOException {
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        backup.write(outputStream);
        return outputStream.toByteArray();
    }

    private static void completeTracker(final RunManager runManager, final String trackerName, final Path screenshot) {
        runManager.executionStarted(List.of(new ScheduledTracker(new TrackerCredential(trackerName, "user", "password"), TrackerType.HEADLESS)));
        runManager.trackerStarted(trackerName);
        runManager.trackerScreenshotSaved(trackerName, RedactionType.BOX, screenshot);
        runManager.trackerCompleted(trackerName, true);
    }

    private static Path createScreenshot(final Path path, final String content) throws IOException {
        Files.createDirectories(path.getParent());
        return Files.writeString(path, content);
    }
}
