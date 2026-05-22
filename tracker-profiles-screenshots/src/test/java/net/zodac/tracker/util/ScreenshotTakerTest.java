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

package net.zodac.tracker.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import net.zodac.tracker.framework.config.Configuration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unit tests for {@link ScreenshotTaker}.
 *
 * <p>
 * Only file-counting behaviour is exercised here. The actual screenshot capture and PNG-write path depend on a live
 * {@link org.openqa.selenium.remote.RemoteWebDriver} and are out of scope until a stubbed browser is available.
 */
class ScreenshotTakerTest {

    @BeforeAll
    static void initConfig() {
        Configuration.init();
    }

    @Test
    void givenEmptyDirectory_whenHowManyScreenshotsAlreadyExist_thenZeroReturned(@TempDir final Path tempDir) {
        assertThat(ScreenshotTaker.howManyScreenshotsAlreadyExist("MyTracker", tempDir))
            .isZero();
    }

    @Test
    void givenOnlyBaseNamePresent_whenHowManyScreenshotsAlreadyExist_thenOneReturned(@TempDir final Path tempDir) throws Exception {
        Files.createFile(tempDir.resolve("MyTracker.png"));

        assertThat(ScreenshotTaker.howManyScreenshotsAlreadyExist("MyTracker", tempDir))
            .isEqualTo(1);
    }

    @Test
    void givenIndexedScreenshots_whenHowManyScreenshotsAlreadyExist_thenAllCounted(@TempDir final Path tempDir) throws Exception {
        Files.createFile(tempDir.resolve("MyTracker.png"));
        Files.createFile(tempDir.resolve("MyTracker_1.png"));
        Files.createFile(tempDir.resolve("MyTracker_2.png"));
        Files.createFile(tempDir.resolve("MyTracker_10.png"));

        assertThat(ScreenshotTaker.howManyScreenshotsAlreadyExist("MyTracker", tempDir))
            .isEqualTo(4);
    }

    @Test
    void givenUnrelatedFiles_whenHowManyScreenshotsAlreadyExist_thenNotCounted(@TempDir final Path tempDir) throws Exception {
        Files.createFile(tempDir.resolve("AnotherTracker.png"));
        Files.createFile(tempDir.resolve("MyTracker.txt"));
        Files.createFile(tempDir.resolve("MyTrackerExtra.png"));

        assertThat(ScreenshotTaker.howManyScreenshotsAlreadyExist("MyTracker", tempDir))
            .isZero();
    }

    @Test
    void givenSuffixWithNonNumericIndex_whenHowManyScreenshotsAlreadyExist_thenNotCounted(@TempDir final Path tempDir) throws Exception {
        Files.createFile(tempDir.resolve("MyTracker_abc.png"));
        Files.createFile(tempDir.resolve("MyTracker_.png"));

        assertThat(ScreenshotTaker.howManyScreenshotsAlreadyExist("MyTracker", tempDir))
            .isZero();
    }

    @Test
    void givenMixOfRelatedAndUnrelated_whenHowManyScreenshotsAlreadyExist_thenOnlyMatchingCounted(@TempDir final Path tempDir) throws Exception {
        Files.createFile(tempDir.resolve("MyTracker.png"));
        Files.createFile(tempDir.resolve("MyTracker_5.png"));
        Files.createFile(tempDir.resolve("MyTrackerOther.png"));
        Files.createFile(tempDir.resolve("MyTracker_abc.png"));
        Files.createFile(tempDir.resolve("notes.txt"));

        assertThat(ScreenshotTaker.howManyScreenshotsAlreadyExist("MyTracker", tempDir))
            .isEqualTo(2);
    }

    @Test
    void givenNonExistentDirectory_whenHowManyScreenshotsAlreadyExist_thenZeroReturned(@TempDir final Path tempDir) {
        final Path doesNotExist = tempDir.resolve("missing-subdirectory");

        assertThat(ScreenshotTaker.howManyScreenshotsAlreadyExist("MyTracker", doesNotExist))
            .isZero();
    }
}
