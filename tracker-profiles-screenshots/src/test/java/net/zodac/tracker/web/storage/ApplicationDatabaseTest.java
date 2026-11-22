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

package net.zodac.tracker.web.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unit tests for {@link ApplicationDatabase}.
 */
class ApplicationDatabaseTest {

    private static final String SECRET_KEY = "test-secret-key-value";
    private static final String LATEST_SCHEMA_VERSION = "4";

    @TempDir
    private Path tempDirectory;

    private ApplicationDatabase applicationDatabase;

    @BeforeEach
    void setUp() throws IOException, SQLException {
        applicationDatabase = ApplicationDatabase.open(databasePath(), SECRET_KEY);
    }

    @AfterEach
    void tearDown() throws SQLException {
        applicationDatabase.close();
    }

    @Test
    void givenNewDatabase_whenOpened_thenSchemaIsMigratedToLatestVersionWithStrictTables() throws SQLException {
        assertThat(querySingleValue("SELECT MAX(CAST(version AS INTEGER)) FROM flyway_schema_history WHERE success = 1"))
            .isEqualTo(LATEST_SCHEMA_VERSION);
        assertThat(querySingleValue("SELECT COUNT(*) FROM sqlite_schema WHERE type = 'table' AND sql LIKE '%STRICT, WITHOUT ROWID'"))
            .isEqualTo("6");
    }

    @Test
    void givenDatabaseCreatedBeforeVersioning_whenOpened_thenItIsMigratedKeepingItsData() throws IOException, SQLException {
        final Path unversionedPath = tempDirectory.resolve("unversioned.db");
        try (final Connection connection = DriverManager.getConnection("jdbc:sqlite:" + unversionedPath.toAbsolutePath());
            final Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE tracker_credentials (name TEXT PRIMARY KEY COLLATE NOCASE, username TEXT NOT NULL DEFAULT '', "
                + "password TEXT NOT NULL DEFAULT '', enabled INTEGER NOT NULL DEFAULT 0)");
            statement.execute("CREATE TABLE settings (name TEXT PRIMARY KEY COLLATE NOCASE, value TEXT NOT NULL)");
            statement.execute("INSERT INTO tracker_credentials (name, username, password, enabled) VALUES ('Tracker', 'user', 'plainPassword', 1)");
            statement.execute("INSERT INTO settings (name, value) VALUES ('LOG_LEVEL', 'DEBUG')");
        }

        try (final ApplicationDatabase migratedDatabase = ApplicationDatabase.open(unversionedPath, SECRET_KEY)) {
            assertThat(migratedDatabase.credentials().findAll())
                .containsExactly(new StoredTrackerCredential("Tracker", "user", "plainPassword", true));
            assertThat(migratedDatabase.settings().findAll())
                .containsExactlyEntriesOf(Map.of("LOG_LEVEL", "DEBUG"));
        }
    }

    @Test
    void givenInvalidBooleanValue_whenWritten_thenItIsRejectedByTheSchema() {
        assertThatThrownBy(() -> executeSql("INSERT INTO tracker_credentials (name, username, password, enabled) VALUES ('Tracker', 'u', '', 2)"))
            .isInstanceOf(SQLException.class)
            .hasMessageContaining("CHECK constraint failed");
    }

    @Test
    void givenSavedCredentials_whenStatusesRead_thenPasswordIsOnlyReportedAsSet() throws SQLException {
        applicationDatabase.credentials().save("Alpha", "user", "password", true);
        applicationDatabase.credentials().save("Beta", "other", "", false);

        assertThat(applicationDatabase.credentials().findAllStatuses())
            .containsExactly(new TrackerCredentialStatus("Alpha", "user", true, true), new TrackerCredentialStatus("Beta", "other", false, false));
        assertThat(applicationDatabase.credentials().hasPassword("alpha"))
            .isTrue();
        assertThat(applicationDatabase.credentials().hasPassword("Beta"))
            .isFalse();
        assertThat(applicationDatabase.credentials().hasPassword("Unknown"))
            .isFalse();
    }

    @Test
    void givenSeveralTrackers_whenEnabledTogether_thenOnlyThoseWithCredentialsAndNotAlreadyEnabledAreChanged() throws SQLException {
        applicationDatabase.credentials().save("Alpha", "user", "password", false);
        applicationDatabase.credentials().save("Beta", "user", "", false);
        applicationDatabase.credentials().save("Gamma", "user", "password", true);

        assertThat(applicationDatabase.credentials().changeEnabled(List.of("Alpha", "Beta", "Gamma", "Unknown"), true))
            .containsExactly("Alpha");
        assertThat(applicationDatabase.credentials().findAllStatuses())
            .extracting(TrackerCredentialStatus::enabled)
            .containsExactly(true, false, true);

        assertThat(applicationDatabase.credentials().changeEnabled(List.of("Alpha", "Beta", "Gamma"), false))
            .containsExactly("Alpha", "Gamma");
    }

    @Test
    void givenSeveralTrackers_whenDeletedTogether_thenOnlyStoredTrackersAreReported() throws SQLException {
        applicationDatabase.credentials().save("Alpha", "user", "password", true);
        applicationDatabase.credentials().save("Beta", "user", "password", true);

        assertThat(applicationDatabase.credentials().deleteAll(List.of("Alpha", "Unknown")))
            .containsExactly("Alpha");
        assertThat(applicationDatabase.credentials().findAllStatuses())
            .extracting(TrackerCredentialStatus::name)
            .containsExactly("Beta");
    }

    @Test
    void givenNewDatabase_whenQueried_thenNoValuesExist() throws SQLException {
        assertThat(applicationDatabase.credentials().findAll())
            .isEmpty();
        assertThat(applicationDatabase.settings().findAll())
            .isEmpty();
    }

    @Test
    void givenSavedCredential_whenSavedWithNullPassword_thenExistingPasswordIsKept() throws SQLException {
        applicationDatabase.credentials().save("Tracker", "user", "password", true);
        applicationDatabase.credentials().save("Tracker", "newUser", null, false);

        assertThat(applicationDatabase.credentials().findAll())
            .containsExactly(new StoredTrackerCredential("Tracker", "newUser", "password", false));
    }

    @Test
    void givenSavedCredential_whenSavedWithEmptyPassword_thenPasswordIsRemoved() throws SQLException {
        applicationDatabase.credentials().save("Tracker", "user", "password", true);
        applicationDatabase.credentials().save("Tracker", "user", "", false);

        assertThat(applicationDatabase.credentials().findAll())
            .containsExactly(new StoredTrackerCredential("Tracker", "user", "", false));
    }

    @Test
    void givenSavedCredential_whenSavedWithDifferentCase_thenSameRowIsUpdated() throws SQLException {
        applicationDatabase.credentials().save("Tracker", "user", "password", true);
        applicationDatabase.credentials().save("TRACKER", "user", "newPassword", true);

        assertThat(applicationDatabase.credentials().findAll())
            .extracting(StoredTrackerCredential::password)
            .containsExactly("newPassword");
    }

    @Test
    void givenMultipleCredentials_whenSavedTogether_thenAllAreStored() throws SQLException {
        applicationDatabase.credentials().saveAll(List.of(
            new StoredTrackerCredential("B", "userB", "passB", true),
            new StoredTrackerCredential("A", "userA", "passA", false)
        ));

        assertThat(applicationDatabase.credentials().findAll())
            .extracting(StoredTrackerCredential::name)
            .containsExactly("A", "B");
    }

    @Test
    void givenSavedCredential_whenDeleted_thenNoLongerExists() throws SQLException {
        applicationDatabase.credentials().save("Tracker", "user", "password", true);
        applicationDatabase.credentials().delete("Tracker");

        assertThat(applicationDatabase.credentials().findAll())
            .isEmpty();
    }

    @Test
    void givenSavedSettings_whenReplaced_thenOnlyNewSettingsExist() throws SQLException {
        applicationDatabase.settings().replaceAll(Map.of("FIRST", "1", "SECOND", "2"));
        applicationDatabase.settings().replaceAll(Map.of("SECOND", "22", "THIRD", "3"));

        assertThat(applicationDatabase.settings().findAll())
            .containsExactlyInAnyOrderEntriesOf(Map.of("SECOND", "22", "THIRD", "3"));
    }

    @Test
    void givenSavedCredential_whenReadFromDatabaseFile_thenPasswordIsEncrypted() throws SQLException {
        applicationDatabase.credentials().save("Tracker", "user", "password", true);

        assertThat(readStoredPassword("Tracker"))
            .startsWith(PasswordCipher.ENCRYPTED_PREFIX)
            .doesNotContain("password");
    }

    @Test
    void givenPlainTextPassword_whenDatabaseIsOpened_thenPasswordIsEncryptedAndReadable() throws IOException, SQLException {
        executeSql("INSERT INTO tracker_credentials (name, username, password, enabled) VALUES ('Tracker', 'user', 'plainPassword', 1)");

        try (final ApplicationDatabase reopenedDatabase = ApplicationDatabase.open(databasePath(), SECRET_KEY)) {
            assertThat(readStoredPassword("Tracker"))
                .startsWith(PasswordCipher.ENCRYPTED_PREFIX);
            assertThat(reopenedDatabase.credentials().findAll())
                .containsExactly(new StoredTrackerCredential("Tracker", "user", "plainPassword", true));
        }
    }

    @Test
    void givenPasswordsEncryptedWithAnotherSecret_whenDatabaseIsOpened_thenExceptionIsThrown() throws SQLException {
        applicationDatabase.credentials().save("Tracker", "user", "password", true);

        assertThatThrownBy(() -> ApplicationDatabase.open(databasePath(), "another-secret-key-value"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("different key");
    }

    @Test
    void givenStoredCredential_whenToString_thenPasswordIsMasked() {
        assertThat(new StoredTrackerCredential("Tracker", "user", "secret", true))
            .hasToString("StoredTrackerCredential[name=Tracker, username=user, password=****, enabled=true]");
    }

    @Test
    void givenSavedResult_whenSavedAgain_thenOnlyLatestResultIsKept() throws SQLException {
        applicationDatabase.results().save(new StoredTrackerResult("Tracker", 1_000L, true));
        applicationDatabase.results().save(new StoredTrackerResult("tracker", 2_000L, false));

        assertThat(applicationDatabase.results().findAll())
            .containsExactly(new StoredTrackerResult("Tracker", 2_000L, false));
    }

    @Test
    void givenSavedScreenshots_whenReplaced_thenOnlyNewScreenshotsOfThatTrackerAreKeptInOrder() throws SQLException {
        applicationDatabase.screenshots().replace("Alpha", List.of(
            new StoredTrackerScreenshot("Alpha", "box", 0, "/screenshots/Alpha_box.png", 1_000L),
            new StoredTrackerScreenshot("Alpha", "blur", 1, "/screenshots/Alpha_blur.png", 1_000L)
        ));
        applicationDatabase.screenshots().replace("Beta", List.of(new StoredTrackerScreenshot("Beta", "none", 0, "/screenshots/Beta.png", 1_000L)));
        applicationDatabase.screenshots().replace("alpha", List.of(
            new StoredTrackerScreenshot("alpha", "blur", 0, "/screenshots/Alpha_blur_2.png", 3_000L),
            new StoredTrackerScreenshot("alpha", "box", 1, "/screenshots/Alpha_box_2.png", 2_000L)
        ));

        assertThat(applicationDatabase.screenshots().findAll())
            .containsExactly(
                new StoredTrackerScreenshot("alpha", "blur", 0, "/screenshots/Alpha_blur_2.png", 3_000L),
                new StoredTrackerScreenshot("alpha", "box", 1, "/screenshots/Alpha_box_2.png", 2_000L),
                new StoredTrackerScreenshot("Beta", "none", 0, "/screenshots/Beta.png", 1_000L)
            );
    }

    @Test
    void givenFewerThanMinimumDurations_whenEstimatesFound_thenTrackerHasNoEstimate() throws SQLException {
        applicationDatabase.durations().add("Tracker", 1_000L, 10_000L);
        applicationDatabase.durations().add("tracker", 2_000L, 20_000L);

        assertThat(applicationDatabase.durations().findEstimates())
            .isEmpty();
    }

    @Test
    void givenMoreThanKeptDurations_whenEstimatesFound_thenOnlyLatestDurationsAreAveraged() throws SQLException {
        // The two oldest are removed, so the average is of 30, 40, 50, 60 and 71 seconds
        for (int i = 1; i <= 6; i++) {
            applicationDatabase.durations().add("Alpha", i * 1_000L, i * 10_000L);
        }
        applicationDatabase.durations().add("alpha", 7_000L, 71_000L);
        applicationDatabase.durations().add("Beta", 1_000L, 1_000L);
        applicationDatabase.durations().add("Beta", 2_000L, 2_000L);
        applicationDatabase.durations().add("Beta", 3_000L, 4_000L);

        assertThat(applicationDatabase.durations().findEstimates())
            .containsExactly(
                new TrackerDurationEstimate("Alpha", 50_200L, 5),
                new TrackerDurationEstimate("Beta", 2_333L, 3)
            );
    }

    @Test
    void givenStoredTrackers_whenEnabledAndDisabledTogether_thenOnlyChangedTrackersAreReported() throws SQLException {
        applicationDatabase.credentials().save("Alpha", "user", "password", false);
        applicationDatabase.credentials().save("Beta", "user", "password", true);
        applicationDatabase.credentials().save("Gamma", "user", "", false);
        applicationDatabase.credentials().save("Delta", "user", "password", true);

        assertThat(applicationDatabase.changeEnabled(List.of("Alpha", "Gamma", "Delta"), List.of("Beta", "Unknown")))
            .containsExactly("Alpha", "Beta");
        assertThat(applicationDatabase.credentials().findAllStatuses())
            .extracting(TrackerCredentialStatus::name, TrackerCredentialStatus::enabled)
            .containsExactlyInAnyOrder(tuple("Alpha", true), tuple("Beta", false), tuple("Gamma", false), tuple("Delta", true));
    }

    @Test
    void givenStoredTrackers_whenRemoved_thenEverythingStoredForThemIsRemovedAndOnlyStoredTrackersAreReported() throws SQLException {
        applicationDatabase.credentials().save("Alpha", "user", "password", true);
        applicationDatabase.results().save(new StoredTrackerResult("Alpha", 1_000L, true));
        applicationDatabase.screenshots().replace("Alpha", List.of(
            new StoredTrackerScreenshot("Alpha", "none", 0, "/screenshots/Alpha.png", 1_000L)
        ));
        for (int i = 1; i <= TrackerDurationRepository.MINIMUM_DURATIONS; i++) {
            applicationDatabase.durations().add("Alpha", i * 1_000L, 10_000L);
        }
        // Only a result and screenshots remain once a tracker's credentials have been removed
        applicationDatabase.results().save(new StoredTrackerResult("Beta", 1_000L, false));
        applicationDatabase.screenshots().replace("Beta", List.of(
            new StoredTrackerScreenshot("Beta", "failure", 0, "/screenshots/Beta.png", 1_000L)
        ));
        applicationDatabase.credentials().save("Gamma", "user", "password", true);
        applicationDatabase.results().save(new StoredTrackerResult("Gamma", 1_000L, true));

        assertThat(applicationDatabase.removeTrackers(List.of("alpha", "Beta", "Unknown")))
            .containsExactly("alpha", "Beta");
        assertThat(applicationDatabase.credentials().findAllStatuses())
            .extracting(TrackerCredentialStatus::name)
            .containsExactly("Gamma");
        assertThat(applicationDatabase.results().findAll())
            .extracting(StoredTrackerResult::name)
            .containsExactly("Gamma");
        assertThat(applicationDatabase.screenshots().findAll())
            .isEmpty();
        assertThat(applicationDatabase.durations().findEstimates())
            .isEmpty();
    }

    @Test
    void givenBackup_whenRestoredInAnotherDatabase_thenItIsMergedWithWhatIsStored() throws IOException, SQLException {
        applicationDatabase.settings().replaceAll(Map.of("LOG_LEVEL", "DEBUG"));
        applicationDatabase.results().save(new StoredTrackerResult("Alpha", 2_000L, true));
        applicationDatabase.screenshots().replace("Alpha", List.of(new StoredTrackerScreenshot("Alpha", "box", 0, "/screenshots/Alpha.png", 2_000L)));
        for (int i = 1; i <= TrackerDurationRepository.MINIMUM_DURATIONS; i++) {
            applicationDatabase.durations().add("Alpha", i * 1_000L, 10_000L);
        }
        final DatabaseBackup backup = applicationDatabase.backup();

        try (final ApplicationDatabase otherDatabase = ApplicationDatabase.open(tempDirectory.resolve("other.db"), "another-secret-key-value")) {
            otherDatabase.settings().replaceAll(Map.of("log_level", "WARN", "NUMBER_OF_SCREENSHOT_ATTEMPTS", "3"));
            otherDatabase.credentials().save("Beta", "other", "password", false);
            otherDatabase.results().save(new StoredTrackerResult("alpha", 1_000L, false));
            otherDatabase.results().save(new StoredTrackerResult("Beta", 1_000L, false));
            otherDatabase.screenshots().replace("Alpha", List.of(new StoredTrackerScreenshot("Alpha", "failure", 0, "/errors/Alpha.png", 1_000L)));
            otherDatabase.durations().add("Alpha", 9_000L, 50_000L);

            otherDatabase.restore(backup, List.of(new StoredTrackerCredential("Alpha", "user", "password", true)));

            assertThat(otherDatabase.settings().findAll())
                .containsExactlyInAnyOrderEntriesOf(Map.of("LOG_LEVEL", "DEBUG", "NUMBER_OF_SCREENSHOT_ATTEMPTS", "3"));
            assertThat(otherDatabase.credentials().findAll())
                .containsExactly(new StoredTrackerCredential("Alpha", "user", "password", true),
                    new StoredTrackerCredential("Beta", "other", "password", false));
            assertThat(otherDatabase.results().findAll())
                .containsExactly(new StoredTrackerResult("alpha", 2_000L, true), new StoredTrackerResult("Beta", 1_000L, false));
            assertThat(otherDatabase.screenshots().findAll())
                .containsExactly(new StoredTrackerScreenshot("Alpha", "box", 0, "/screenshots/Alpha.png", 2_000L));
            assertThat(otherDatabase.durations().findEstimates())
                .containsExactly(new TrackerDurationEstimate("Alpha", 10_000L, TrackerDurationRepository.MINIMUM_DURATIONS));
        }
    }

    @Test
    void givenInvalidBackups_whenRestored_thenExceptionIsThrownAndNothingIsChanged() throws SQLException {
        applicationDatabase.settings().replaceAll(Map.of("LOG_LEVEL", "DEBUG"));
        final DatabaseBackup duplicateResults = new DatabaseBackup(Map.of(), List.of(new StoredTrackerResult("Alpha", 1_000L, true),
            new StoredTrackerResult("alpha", 2_000L, true)), List.of(), List.of());
        final DatabaseBackup negativeDuration = new DatabaseBackup(Map.of(), List.of(), List.of(),
            List.of(new StoredTrackerDuration("Alpha", 1_000L, -1L)));

        assertThatThrownBy(() -> applicationDatabase.restore(duplicateResults, List.of()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("more than one entry for 'alpha'");
        assertThatThrownBy(() -> applicationDatabase.restore(negativeDuration, List.of()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("an entry of durations is incomplete");
        assertThat(applicationDatabase.settings().findAll())
            .containsExactlyEntriesOf(Map.of("LOG_LEVEL", "DEBUG"));
    }

    private Path databasePath() {
        return tempDirectory.resolve("nested").resolve("test.db");
    }

    private String readStoredPassword(final String name) throws SQLException {
        try (final Connection connection = DriverManager.getConnection("jdbc:sqlite:" + databasePath().toAbsolutePath());
            final PreparedStatement statement = connection.prepareStatement("SELECT password FROM tracker_credentials WHERE name = ?")) {
            statement.setString(1, name);
            try (final ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getString(1) : "";
            }
        }
    }

    private String querySingleValue(final String sql) throws SQLException {
        try (final Connection connection = DriverManager.getConnection("jdbc:sqlite:" + databasePath().toAbsolutePath());
            final Statement statement = connection.createStatement();
            final ResultSet resultSet = statement.executeQuery(sql)) {
            return resultSet.next() ? resultSet.getString(1) : "";
        }
    }

    private void executeSql(final String sql) throws SQLException {
        try (final Connection connection = DriverManager.getConnection("jdbc:sqlite:" + databasePath().toAbsolutePath());
            final Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }
}
