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

package net.zodac.tracker.web.http;

import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.zodac.tracker.framework.config.Configuration;
import net.zodac.tracker.web.RunManager;
import net.zodac.tracker.web.screenshot.ScreenshotArchive;
import net.zodac.tracker.web.screenshot.ScreenshotFile;
import net.zodac.tracker.web.screenshot.ScreenshotPreviews;
import org.jspecify.annotations.Nullable;

/**
 * Serves the screenshots of the current (or most recent) execution.
 *
 * <p>
 * Handles {@code GET} requests to:
 * <ul>
 *     <li>{@code /api/screenshots}: downloads the profile screenshots of every successful tracker</li>
 *     <li>{@code /api/screenshots?tracker={name}}: downloads every screenshot of the tracker</li>
 *     <li>{@code /api/screenshots?tracker={name}&id={id}}: a single screenshot, shown in the browser, or downloaded with {@code &download=true}.
 *     Its thumbnail is retrieved with {@code &thumbnail=true}, and its preview (scaled to 1080p, or the screenshot itself if it is not wider)
 *     with {@code &preview=true}</li>
 * </ul>
 *
 * <p>
 * A download of a single screenshot is the {@code .png} file, while several screenshots are downloaded as a {@code .zip} archive (see
 * {@link ScreenshotArchive#fileName(long, java.time.ZoneId)}). Only screenshots listed in the {@link net.zodac.tracker.web.RunStatus} are served,
 * so no other file can be requested.
 */
public final class ScreenshotApiHandler extends ApiHandler {

    /**
     * The context path for this handler.
     */
    public static final String CONTEXT_PATH = "/api/screenshots";

    private static final String PNG_CONTENT_TYPE = "image/png";
    private static final String JPEG_CONTENT_TYPE = "image/jpeg";
    private static final String ZIP_CONTENT_TYPE = "application/zip";
    private static final int CHUNKED_RESPONSE = 0;

    private final RunManager runManager;
    private final ScreenshotPreviews screenshotPreviews;

    private ScreenshotApiHandler(final RunManager runManager, final ScreenshotPreviews screenshotPreviews) {
        super(CONTEXT_PATH);
        this.runManager = runManager;
        this.screenshotPreviews = screenshotPreviews;
    }

    /**
     * Creates a {@link ScreenshotApiHandler}.
     *
     * @param runManager the {@link RunManager} holding the screenshots of the current (or most recent) execution
     * @return the {@link ScreenshotApiHandler}
     */
    public static ScreenshotApiHandler create(final RunManager runManager) {
        return new ScreenshotApiHandler(runManager, ScreenshotPreviews.create());
    }

    @Override
    protected void handleGet(final HttpExchange exchange, final @Nullable String pathParameter) throws IOException {
        if (pathParameter != null) {
            throw new HttpRequestException(HttpStatus.NOT_FOUND, "Unknown path: '%s'".formatted(pathParameter));
        }

        final Map<String, String> query = queryParameters(exchange);
        final String trackerName = query.get("tracker");
        if (trackerName == null) {
            sendDownload(exchange, runManager.successfulScreenshots());
            return;
        }

        final List<ScreenshotFile> trackerScreenshots = runManager.trackerScreenshots(trackerName);
        final String id = query.get("id");
        if (id == null) {
            sendDownload(exchange, trackerScreenshots);
            return;
        }

        final ScreenshotFile screenshot = trackerScreenshots
            .stream()
            .filter(trackerScreenshot -> trackerScreenshot.id().equals(id))
            .findFirst()
            .filter(trackerScreenshot -> Files.isRegularFile(trackerScreenshot.path()))
            .orElseThrow(() -> new HttpRequestException(HttpStatus.NOT_FOUND, "No '%s' screenshot found for '%s'".formatted(id, trackerName)));

        if (Boolean.parseBoolean(query.get("thumbnail"))) {
            sendJpeg(exchange, screenshotPreviews.thumbnail(screenshot.path()));
            return;
        }

        if (Boolean.parseBoolean(query.get("preview"))) {
            final Optional<byte[]> preview = screenshotPreviews.preview(screenshot.path());
            if (preview.isPresent()) {
                sendJpeg(exchange, preview.get());
                return;
            }
        }

        sendScreenshot(exchange, screenshot, Boolean.parseBoolean(query.get("download")));
    }

    private static void sendJpeg(final HttpExchange exchange, final byte[] image) throws IOException {
        setHeaders(exchange, JPEG_CONTENT_TYPE, true, null);
        exchange.sendResponseHeaders(HttpStatus.OK.code(), image.length);
        try (final OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(image);
        }
    }

    private void sendDownload(final HttpExchange exchange, final List<ScreenshotFile> screenshots) throws IOException {
        final List<ScreenshotFile> existingScreenshots = screenshots
            .stream()
            .filter(screenshot -> Files.isRegularFile(screenshot.path()))
            .toList();

        if (existingScreenshots.isEmpty()) {
            throw new HttpRequestException(HttpStatus.NOT_FOUND, "No screenshots found");
        }

        if (existingScreenshots.size() == 1) {
            sendScreenshot(exchange, existingScreenshots.getFirst(), true);
            return;
        }

        final Long executionStartedAt = runManager.executionStartedAt();
        final String fileName = ScreenshotArchive.fileName(executionStartedAt == null ? System.currentTimeMillis() : executionStartedAt,
            Configuration.get().timezone());
        setHeaders(exchange, ZIP_CONTENT_TYPE, false, contentDisposition("attachment", fileName));
        exchange.sendResponseHeaders(HttpStatus.OK.code(), CHUNKED_RESPONSE);
        try (final OutputStream outputStream = exchange.getResponseBody()) {
            ScreenshotArchive.write(existingScreenshots, outputStream);
        }
    }

    private static void sendScreenshot(final HttpExchange exchange, final ScreenshotFile screenshot, final boolean download) throws IOException {
        setHeaders(exchange, PNG_CONTENT_TYPE, !download, contentDisposition(download ? "attachment" : "inline", screenshot.fileName()));
        exchange.sendResponseHeaders(HttpStatus.OK.code(), Files.size(screenshot.path()));
        try (final OutputStream outputStream = exchange.getResponseBody()) {
            Files.copy(screenshot.path(), outputStream);
        }
    }

    // A screenshot shown in the browser is cached, since the web UI adds when it was saved to its URL (so a replaced screenshot has a new URL)
    private static void setHeaders(final HttpExchange exchange, final String contentType, final boolean cacheable,
        final @Nullable String contentDisposition) {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("Cache-Control", cacheable ? "private, max-age=86400" : "no-store");
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        if (contentDisposition != null) {
            exchange.getResponseHeaders().set("Content-Disposition", contentDisposition);
        }
    }

    // The plain file name is limited to printable ASCII, with the full name in the RFC 6266 'filename*' parameter
    private static String contentDisposition(final String type, final String fileName) {
        final String asciiFileName = fileName.replaceAll("[^\\x20-\\x7E]|[\"\\\\]", "_");
        final String encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        return "%s; filename=\"%s\"; filename*=UTF-8''%s".formatted(type, asciiFileName, encodedFileName);
    }
}
