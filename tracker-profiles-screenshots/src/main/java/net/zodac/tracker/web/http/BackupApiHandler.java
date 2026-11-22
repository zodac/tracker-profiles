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
import java.io.InputStream;
import java.io.OutputStream;
import java.sql.SQLException;
import java.util.Locale;
import java.util.Map;
import net.zodac.tracker.web.backup.BackupService;
import org.jspecify.annotations.Nullable;

/**
 * Handles requests to download a backup ({@code GET ?credentials=true&screenshots=true}, a zip file, see
 * {@link net.zodac.tracker.web.backup.BackupArchive}), and to restore one ({@code POST}, with the zip file as the request body and a
 * {@code Content-Type} of {@value #ZIP_CONTENT_TYPE}), such as to move the application to another machine.
 */
public final class BackupApiHandler extends ApiHandler {

    /**
     * The context path for this handler.
     */
    public static final String CONTEXT_PATH = "/api/backup";

    private static final String ZIP_CONTENT_TYPE = "application/zip";
    private static final int CHUNKED_RESPONSE = 0;

    private final BackupService backupService;

    private BackupApiHandler(final BackupService backupService) {
        super(CONTEXT_PATH);
        this.backupService = backupService;
    }

    /**
     * Creates a {@link BackupApiHandler}.
     *
     * @param backupService the {@link BackupService} that creates and restores backups
     * @return the {@link BackupApiHandler}
     */
    public static BackupApiHandler create(final BackupService backupService) {
        return new BackupApiHandler(backupService);
    }

    @Override
    protected void handleGet(final HttpExchange exchange, final @Nullable String pathParameter) throws IOException, SQLException {
        final Map<String, String> query = queryParameters(exchange);
        final BackupService.Backup backup = backupService.prepare(Boolean.parseBoolean(query.get("credentials")),
            Boolean.parseBoolean(query.get("screenshots")));

        exchange.getResponseHeaders().set("Content-Type", ZIP_CONTENT_TYPE);
        exchange.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"%s\"".formatted(backup.fileName()));
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.sendResponseHeaders(HttpStatus.OK.code(), CHUNKED_RESPONSE);
        try (final OutputStream outputStream = exchange.getResponseBody()) {
            backup.write(outputStream);
        }
    }

    @Override
    protected void handlePost(final HttpExchange exchange, final @Nullable String pathParameter) throws IOException, SQLException {
        try (final InputStream inputStream = exchange.getRequestBody()) {
            sendJson(exchange, HttpStatus.OK, backupService.restore(inputStream));
        } catch (final IllegalArgumentException e) {
            throw new HttpRequestException(HttpStatus.BAD_REQUEST, e);
        } catch (final IllegalStateException e) {
            throw new HttpRequestException(HttpStatus.CONFLICT, e);
        }
    }

    // A zip file cannot be sent cross-origin without a CORS preflight either, so this is as safe as JSON
    @Override
    protected boolean acceptsContentType(final String contentType) {
        return contentType.toLowerCase(Locale.ROOT).startsWith(ZIP_CONTENT_TYPE);
    }
}
