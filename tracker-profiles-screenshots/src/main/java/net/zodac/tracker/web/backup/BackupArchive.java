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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.jspecify.annotations.Nullable;

/**
 * Writes and reads a backup archive: a zip file with {@value #MANIFEST_ENTRY} (the {@link BackupManifest}) first, then {@value #CREDENTIALS_ENTRY}
 * (the tracker credentials, in the format of the CSV export) if included, then the screenshot files under {@value #SCREENSHOTS_DIRECTORY} if
 * included. Since the manifest comes first, an archive is read (and its screenshots restored) as it is uploaded, without being saved first.
 */
public final class BackupArchive {

    /**
     * The name of the {@link BackupManifest} in the archive.
     */
    public static final String MANIFEST_ENTRY = "backup.json";

    /**
     * The name of the tracker credentials in the archive.
     */
    public static final String CREDENTIALS_ENTRY = "trackers.csv";

    /**
     * The directory of the screenshot files in the archive.
     */
    public static final String SCREENSHOTS_DIRECTORY = "screenshots/";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private BackupArchive() {

    }

    /**
     * Writes a backup archive.
     *
     * @param outputStream    the {@link OutputStream} to write to (not closed)
     * @param manifest        the {@link BackupManifest}
     * @param credentials     the tracker credentials in the format of the CSV export, or {@code null} if they are not included
     * @param screenshotFiles the screenshot files to include (which must exist), keyed by their name under {@value #SCREENSHOTS_DIRECTORY}
     * @throws IOException thrown if the archive cannot be written, or a screenshot cannot be read
     */
    public static void write(final OutputStream outputStream, final BackupManifest manifest, final @Nullable String credentials,
        final Map<String, Path> screenshotFiles) throws IOException {
        final ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream, StandardCharsets.UTF_8);
        zipOutputStream.putNextEntry(new ZipEntry(MANIFEST_ENTRY));
        zipOutputStream.write(GSON.toJson(manifest).getBytes(StandardCharsets.UTF_8));
        zipOutputStream.closeEntry();

        if (credentials != null) {
            zipOutputStream.putNextEntry(new ZipEntry(CREDENTIALS_ENTRY));
            zipOutputStream.write(credentials.getBytes(StandardCharsets.UTF_8));
            zipOutputStream.closeEntry();
        }

        for (final Map.Entry<String, Path> screenshotFile : screenshotFiles.entrySet()) {
            zipOutputStream.putNextEntry(new ZipEntry(SCREENSHOTS_DIRECTORY + screenshotFile.getKey()));
            Files.copy(screenshotFile.getValue(), zipOutputStream);
            zipOutputStream.closeEntry();
        }
        zipOutputStream.finish();
    }

    /**
     * Reads a backup archive, restoring each screenshot file it includes (that is listed in its {@link BackupManifest}) into the screenshot folder,
     * replacing any file with the same name. The manifest, and the credentials if included, are validated before any screenshot is restored.
     *
     * @param inputStream      the {@link InputStream} of the archive (not closed)
     * @param screenshotFolder the folder in which the screenshot files are restored, at the same path relative to it as in the archive
     * @return the {@link Content} of the archive
     * @throws IOException              thrown if the archive cannot be read, or a screenshot cannot be written
     * @throws IllegalArgumentException thrown if the file is not a valid backup
     */
    public static Content read(final InputStream inputStream, final Path screenshotFolder) throws IOException {
        final Path folder = screenshotFolder.toAbsolutePath().normalize();
        BackupManifest manifest = null;
        String credentials = null;
        final Map<String, String> pathsByFileName = new HashMap<>();
        final Map<String, Path> restoredFiles = new HashMap<>();

        try {
            final ZipInputStream zipInputStream = new ZipInputStream(inputStream, StandardCharsets.UTF_8);
            for (ZipEntry entry = zipInputStream.getNextEntry(); entry != null; entry = zipInputStream.getNextEntry()) {
                final String name = entry.getName();
                if (manifest == null) {
                    if (!MANIFEST_ENTRY.equals(name)) {
                        throw notBackup();
                    }
                    manifest = readManifest(zipInputStream);
                    manifest.screenshotFiles().forEach((path, fileName) -> pathsByFileName.put(fileName, path));
                } else if (CREDENTIALS_ENTRY.equals(name) && manifest.credentialsIncluded()) {
                    credentials = new String(zipInputStream.readAllBytes(), StandardCharsets.UTF_8);
                } else if (name.startsWith(SCREENSHOTS_DIRECTORY) && pathsByFileName.containsKey(name.substring(SCREENSHOTS_DIRECTORY.length()))) {
                    // The credentials come before the screenshots, so nothing is restored from a backup without the credentials it should have
                    requireCredentials(manifest, credentials);
                    final String fileName = name.substring(SCREENSHOTS_DIRECTORY.length());
                    restoredFiles.put(pathsByFileName.get(fileName), restoreScreenshot(zipInputStream, folder, fileName));
                }
            }
        } catch (final ZipException e) {
            throw new IllegalArgumentException("The file is not a valid zip file: %s".formatted(e.getMessage()), e);
        }

        if (manifest == null) {
            throw notBackup();
        }
        requireCredentials(manifest, credentials);
        return new Content(manifest, credentials, Map.copyOf(restoredFiles));
    }

    private static BackupManifest readManifest(final InputStream inputStream) throws IOException {
        final BackupManifest manifest;
        try {
            manifest = GSON.fromJson(new String(inputStream.readAllBytes(), StandardCharsets.UTF_8), BackupManifest.class);
        } catch (final JsonParseException e) {
            throw new IllegalArgumentException("The backup is invalid: %s is not valid JSON".formatted(MANIFEST_ENTRY), e);
        }
        if (manifest == null) {
            throw notBackup();
        }
        manifest.validate();
        return manifest;
    }

    private static void requireCredentials(final BackupManifest manifest, final @Nullable String credentials) {
        if (manifest.credentialsIncluded() && credentials == null) {
            throw new IllegalArgumentException("The backup is invalid: %s is missing".formatted(CREDENTIALS_ENTRY));
        }
    }

    private static Path restoreScreenshot(final InputStream inputStream, final Path folder, final String fileName) throws IOException {
        final Path destination = folder.resolve(fileName).normalize();
        // Already checked by the manifest, but a file must never be written outside the screenshot folder
        if (!destination.startsWith(folder) || destination.getParent() == null) {
            throw new IllegalArgumentException("The backup is invalid: '%s' is outside the screenshot folder".formatted(fileName));
        }
        Files.createDirectories(destination.getParent());
        Files.copy(inputStream, destination, StandardCopyOption.REPLACE_EXISTING);
        return destination;
    }

    private static IllegalArgumentException notBackup() {
        return new IllegalArgumentException("The file is not a backup of tracker-profiles");
    }

    /**
     * The content of a backup archive that has been read.
     *
     * @param manifest      the {@link BackupManifest}
     * @param credentials   the tracker credentials in the format of the CSV export, or {@code null} if they are not included
     * @param restoredFiles the path of each screenshot file restored into the screenshot folder, keyed by the path stored for the screenshot in
     *                      the backup
     */
    public record Content(BackupManifest manifest, @Nullable String credentials, Map<String, Path> restoredFiles) {

    }
}
