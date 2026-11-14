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
import java.sql.SQLException;
import java.util.Map;
import net.zodac.tracker.web.SettingsService;
import org.jspecify.annotations.Nullable;

/**
 * Handles requests to list the application settings ({@code GET}), save changed settings ({@code PUT}), and reset a setting to its environment
 * variable or default value ({@code DELETE /api/settings/{name}}).
 */
public final class SettingsApiHandler extends ApiHandler {

    /**
     * The context path for this handler.
     */
    public static final String CONTEXT_PATH = "/api/settings";

    private final SettingsService settingsService;

    private SettingsApiHandler(final SettingsService settingsService) {
        super(CONTEXT_PATH);
        this.settingsService = settingsService;
    }

    /**
     * Creates a {@link SettingsApiHandler}.
     *
     * @param settingsService the {@link SettingsService} used to read and update settings
     * @return the {@link SettingsApiHandler}
     */
    public static SettingsApiHandler create(final SettingsService settingsService) {
        return new SettingsApiHandler(settingsService);
    }

    @Override
    protected void handleGet(final HttpExchange exchange, final @Nullable String pathParameter) throws IOException, SQLException {
        sendJson(exchange, HttpStatus.OK, settingsService.listSettings());
    }

    @Override
    protected void handlePut(final HttpExchange exchange, final @Nullable String pathParameter) throws IOException, SQLException {
        final SettingsUpdate settingsUpdate = readJson(exchange, SettingsUpdate.class);
        if (settingsUpdate.values() == null || settingsUpdate.values().isEmpty()) {
            throw new HttpRequestException(HttpStatus.BAD_REQUEST, "No settings provided");
        }

        try {
            sendJson(exchange, HttpStatus.OK, settingsService.updateSettings(settingsUpdate.values()));
        } catch (final IllegalArgumentException e) {
            throw new HttpRequestException(HttpStatus.BAD_REQUEST, e);
        } catch (final IllegalStateException e) {
            throw new HttpRequestException(HttpStatus.CONFLICT, e);
        }
    }

    @Override
    protected void handleDelete(final HttpExchange exchange, final @Nullable String pathParameter) throws IOException, SQLException {
        if (pathParameter == null) {
            throw new HttpRequestException(HttpStatus.BAD_REQUEST, "Setting name must be provided");
        }

        try {
            sendJson(exchange, HttpStatus.OK, settingsService.resetSetting(pathParameter));
        } catch (final IllegalArgumentException e) {
            throw new HttpRequestException(HttpStatus.BAD_REQUEST, e);
        } catch (final IllegalStateException e) {
            throw new HttpRequestException(HttpStatus.CONFLICT, e);
        }
    }

    /**
     * The request body to save settings.
     *
     * @param values the values to save, keyed by environment variable name
     */
    record SettingsUpdate(@Nullable Map<String, String> values) {

    }
}
