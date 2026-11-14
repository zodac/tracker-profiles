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

package net.zodac.tracker.web.screenshot;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import net.zodac.tracker.framework.redactor.RedactionType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unit tests for {@link ScreenshotArchive}.
 */
class ScreenshotArchiveTest {

    // 2026-11-13T23:19:00Z
    private static final long EXECUTION_STARTED_AT = 1_794_611_940_000L;

    @TempDir
    private Path tempDirectory;

    @Test
    void givenStartTime_whenFileNameCreated_thenVersionAndDateInTimezoneAreUsed() {
        assertThat(ScreenshotArchive.fileName(EXECUTION_STARTED_AT, ZoneId.of("UTC"), "13.0.0"))
            .isEqualTo("tracker-profiles_v13.0.0_2026-11-13_23:19:00.zip");
        assertThat(ScreenshotArchive.fileName(EXECUTION_STARTED_AT, ZoneId.of("America/New_York"), "13.0.0"))
            .isEqualTo("tracker-profiles_v13.0.0_2026-11-13_18:19:00.zip");
        assertThat(ScreenshotArchive.fileName(EXECUTION_STARTED_AT, ZoneId.of("Asia/Tokyo"), "13.0.0"))
            .isEqualTo("tracker-profiles_v13.0.0_2026-11-14_08:19:00.zip");
    }

    @Test
    void givenScreenshots_whenWritten_thenEachIsIncludedWithFailuresInErrorsDirectory() throws IOException {
        final Path errors = Files.createDirectories(tempDirectory.resolve("errors"));
        final List<ScreenshotFile> screenshots = List.of(
            ScreenshotFile.ofProfile(RedactionType.BOX, write(tempDirectory.resolve("Tracker_Box.png"), "box"), 0L),
            ScreenshotFile.ofProfile(RedactionType.BLUR, write(tempDirectory.resolve("Tracker_Blur.png"), "blur"), 0L),
            ScreenshotFile.ofFailure(write(errors.resolve("Other.png"), "failure"), 0L)
        );

        assertThat(entries(screenshots))
            .containsExactly("Tracker_Box.png=box", "Tracker_Blur.png=blur", "errors/Other.png=failure");
    }

    @Test
    void givenScreenshotsWithSameFileName_whenWritten_thenLaterOnesAreGivenAnIndex() throws IOException {
        final Path first = write(Files.createDirectories(tempDirectory.resolve("first")).resolve("Tracker.png"), "first");
        final Path second = write(Files.createDirectories(tempDirectory.resolve("second")).resolve("Tracker.png"), "second");
        final List<ScreenshotFile> screenshots = List.of(
            ScreenshotFile.ofProfile(RedactionType.NONE, first, 0L),
            ScreenshotFile.ofProfile(RedactionType.NONE, second, 0L)
        );

        assertThat(entries(screenshots))
            .containsExactly("Tracker.png=first", "Tracker_1.png=second");
    }

    private static Path write(final Path path, final String content) throws IOException {
        return Files.writeString(path, content);
    }

    private static List<String> entries(final List<ScreenshotFile> screenshots) throws IOException {
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ScreenshotArchive.write(screenshots, outputStream);

        final List<String> entries = new ArrayList<>();
        try (final ZipInputStream zipInputStream = new ZipInputStream(new ByteArrayInputStream(outputStream.toByteArray()))) {
            ZipEntry entry = zipInputStream.getNextEntry();
            while (entry != null) {
                entries.add("%s=%s".formatted(entry.getName(), new String(zipInputStream.readAllBytes(), StandardCharsets.UTF_8)));
                entry = zipInputStream.getNextEntry();
            }
        }
        return entries;
    }
}
