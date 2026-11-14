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

import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unit tests for {@link ApplicationDatabase}.
 */
class ApplicationDatabaseTest {

    @TempDir
    private Path tempDirectory;

    private ApplicationDatabase applicationDatabase;

    @BeforeEach
    void setUp() throws IOException, SQLException {
        applicationDatabase = ApplicationDatabase.open(tempDirectory.resolve("nested").resolve("test.db"));
    }

    @Test
    void givenNewDatabase_whenQueried_thenNoValuesExist() throws SQLException {
        assertThat(applicationDatabase.hasCredentials())
            .isFalse();
        assertThat(applicationDatabase.findAllCredentials())
            .isEmpty();
        assertThat(applicationDatabase.findAllSettings())
            .isEmpty();
    }

    @Test
    void givenSavedCredential_whenSavedWithNullPassword_thenExistingPasswordIsKept() throws SQLException {
        applicationDatabase.saveCredential("Tracker", "user", "password", true);
        applicationDatabase.saveCredential("Tracker", "newUser", null, false);

        assertThat(applicationDatabase.findAllCredentials())
            .containsExactly(new StoredTrackerCredential("Tracker", "newUser", "password", false));
    }

    @Test
    void givenSavedCredential_whenSavedWithDifferentCase_thenSameRowIsUpdated() throws SQLException {
        applicationDatabase.saveCredential("Tracker", "user", "password", true);
        applicationDatabase.saveCredential("TRACKER", "user", "newPassword", true);

        assertThat(applicationDatabase.findAllCredentials())
            .extracting(StoredTrackerCredential::password)
            .containsExactly("newPassword");
    }

    @Test
    void givenMultipleCredentials_whenSavedTogether_thenAllAreStored() throws SQLException {
        applicationDatabase.saveCredentials(List.of(
            new StoredTrackerCredential("B", "userB", "passB", true),
            new StoredTrackerCredential("A", "userA", "passA", false)
        ));

        assertThat(applicationDatabase.hasCredentials())
            .isTrue();
        assertThat(applicationDatabase.findAllCredentials())
            .extracting(StoredTrackerCredential::name)
            .containsExactly("A", "B");
    }

    @Test
    void givenSavedCredential_whenDeleted_thenNoLongerExists() throws SQLException {
        applicationDatabase.saveCredential("Tracker", "user", "password", true);
        applicationDatabase.deleteCredential("Tracker");

        assertThat(applicationDatabase.findAllCredentials())
            .isEmpty();
    }

    @Test
    void givenSavedSettings_whenReplaced_thenOnlyNewSettingsExist() throws SQLException {
        applicationDatabase.replaceSettings(Map.of("FIRST", "1", "SECOND", "2"));
        applicationDatabase.replaceSettings(Map.of("SECOND", "22", "THIRD", "3"));

        assertThat(applicationDatabase.findAllSettings())
            .containsExactlyInAnyOrderEntriesOf(Map.of("SECOND", "22", "THIRD", "3"));
    }

    @Test
    void givenStoredCredential_whenToString_thenPasswordIsMasked() {
        assertThat(new StoredTrackerCredential("Tracker", "user", "secret", true))
            .hasToString("StoredTrackerCredential[name=Tracker, username=user, password=****, enabled=true]");
    }

    @Test
    void givenSavedResult_whenSavedAgain_thenOnlyLatestResultIsKept() throws SQLException {
        applicationDatabase.saveResult(new StoredTrackerResult("Tracker", 1_000L, true));
        applicationDatabase.saveResult(new StoredTrackerResult("tracker", 2_000L, false));

        assertThat(applicationDatabase.findAllResults())
            .containsExactly(new StoredTrackerResult("Tracker", 2_000L, false));
    }
}
