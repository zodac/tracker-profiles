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

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import net.zodac.tracker.framework.ApplicationVersion;

/**
 * Creates the {@code .zip} archives used to download several screenshots at once.
 */
public final class ScreenshotArchive {

    private static final DateTimeFormatter ARCHIVE_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH:mm:ss", Locale.ROOT);
    private static final String ERRORS_DIRECTORY = "errors/";
    private static final String PNG_EXTENSION = ".png";

    private ScreenshotArchive() {

    }

    /**
     * The name of an archive of screenshots, in the format {@code tracker-profiles_vVERSION_yyyy-MM-dd_HH:mm:ss.zip}.
     *
     * @param executionStartedAt the start time of the execution in which the screenshots were taken, as epoch milliseconds
     * @param timezone           the {@link ZoneId} in which the start time is formatted
     * @param version            the version of the application
     * @return the archive file name
     */
    public static String fileName(final long executionStartedAt, final ZoneId timezone, final String version) {
        final String date = ARCHIVE_DATE_FORMAT.format(Instant.ofEpochMilli(executionStartedAt).atZone(timezone));
        return "tracker-profiles_v%s_%s.zip".formatted(version, date);
    }

    /**
     * The name of an archive of screenshots for the current version of the application.
     *
     * @param executionStartedAt the start time of the execution in which the screenshots were taken, as epoch milliseconds
     * @param timezone           the {@link ZoneId} in which the start time is formatted
     * @return the archive file name
     * @see #fileName(long, ZoneId, String)
     */
    public static String fileName(final long executionStartedAt, final ZoneId timezone) {
        return fileName(executionStartedAt, timezone, ApplicationVersion.current());
    }

    /**
     * Writes the screenshots to a {@code .zip} archive. Each screenshot is stored by its file name, with failure screenshots in an {@code errors/}
     * directory (as they are saved). Screenshots are already compressed, so are stored without further compression.
     *
     * @param screenshots  the {@link ScreenshotFile}s to include
     * @param outputStream the {@link OutputStream} to write the archive to, which is not closed
     * @throws IOException thrown if a screenshot cannot be read, or the archive cannot be written
     */
    public static void write(final List<ScreenshotFile> screenshots, final OutputStream outputStream) throws IOException {
        // Finished rather than closed, so the given stream is left open
        final ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream);
        zipOutputStream.setLevel(Deflater.NO_COMPRESSION);

        final Set<String> entryNames = new HashSet<>();
        for (final ScreenshotFile screenshot : screenshots) {
            zipOutputStream.putNextEntry(new ZipEntry(uniqueEntryName(entryName(screenshot), entryNames)));
            Files.copy(screenshot.path(), zipOutputStream);
            zipOutputStream.closeEntry();
        }
        zipOutputStream.finish();
    }

    private static String entryName(final ScreenshotFile screenshot) {
        return screenshot.failure() ? ERRORS_DIRECTORY + screenshot.fileName() : screenshot.fileName();
    }

    // An archive cannot hold two entries with the same name, so a later duplicate is given an index
    private static String uniqueEntryName(final String entryName, final Set<String> entryNames) {
        if (entryNames.add(entryName)) {
            return entryName;
        }

        final String baseName = entryName.endsWith(PNG_EXTENSION) ? entryName.substring(0, entryName.length() - PNG_EXTENSION.length()) : entryName;
        final String extension = entryName.substring(baseName.length());
        int index = 1;
        while (!entryNames.add("%s_%d%s".formatted(baseName, index, extension))) {
            index++;
        }
        return "%s_%d%s".formatted(baseName, index, extension);
    }
}
