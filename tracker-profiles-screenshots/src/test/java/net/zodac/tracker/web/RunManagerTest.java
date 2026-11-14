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

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import net.zodac.tracker.framework.config.Configuration;
import net.zodac.tracker.web.log.WebLogAppender;
import net.zodac.tracker.web.storage.ApplicationDatabase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unit tests for {@link RunManager}.
 */
class RunManagerTest {

    @TempDir
    private Path tempDirectory;

    @AfterEach
    void tearDown() {
        Configuration.reload(Map.of());
    }

    @Test
    void givenNoExecution_whenCancelled_thenNothingIsCancelled() throws IOException, SQLException {
        final RunManager runManager = RunManager.create(ApplicationDatabase.open(tempDirectory.resolve("test.db")), WebLogAppender.install());

        assertThat(runManager.cancel())
            .isFalse();
        assertThat(runManager.status().state())
            .isEqualTo(RunStatus.State.IDLE);
    }

    @Test
    void givenNoExecution_whenStatusRetrieved_thenNoProgressIsReported() throws IOException, SQLException {
        final RunManager runManager = RunManager.create(ApplicationDatabase.open(tempDirectory.resolve("test.db")), WebLogAppender.install());

        assertThat(runManager.status())
            .returns(0, RunStatus::totalTrackers)
            .returns(List.of(), RunStatus::trackers)
            .returns(List.of(), RunStatus::pendingInputs);
    }

    @Test
    void givenNoExecution_whenSettingsSaved_thenWholeConfigurationIsReloaded() throws IOException, SQLException {
        Configuration.reload(Map.of());
        final RunManager runManager = RunManager.create(ApplicationDatabase.open(tempDirectory.resolve("test.db")), WebLogAppender.install());

        runManager.applySavedSettings(Map.of("NUMBER_OF_SCREENSHOT_ATTEMPTS", "3", "NUMBER_OF_PARALLEL_THREADS", "4"));

        assertThat(Configuration.get().numberOfScreenshotAttempts())
            .isEqualTo(3);
        assertThat(Configuration.get().numberOfParallelThreads())
            .isEqualTo(4);
    }
}
