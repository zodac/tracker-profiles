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
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import net.zodac.tracker.web.log.WebLogAppender;
import org.jspecify.annotations.Nullable;

/**
 * Handles requests for the log messages of the current execution: {@code GET /api/logs?tracker={name}&after={id}} returns the messages logged by a
 * tracker (or, without {@code tracker}, the messages not logged by any tracker) with an ID greater than {@code after}, oldest first. The web UI polls
 * this with the ID of the last message it has, so only new messages are returned.
 */
public final class LogApiHandler extends ApiHandler {

    /**
     * The context path for this handler.
     */
    public static final String CONTEXT_PATH = "/api/logs";

    private final WebLogAppender webLogAppender;

    private LogApiHandler(final WebLogAppender webLogAppender) {
        super(CONTEXT_PATH);
        this.webLogAppender = webLogAppender;
    }

    /**
     * Creates a {@link LogApiHandler}.
     *
     * @param webLogAppender the {@link WebLogAppender} holding the log messages
     * @return the {@link LogApiHandler}
     */
    public static LogApiHandler create(final WebLogAppender webLogAppender) {
        return new LogApiHandler(webLogAppender);
    }

    @Override
    protected void handleGet(final HttpExchange exchange, final @Nullable String pathParameter) throws IOException {
        final Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());
        sendJson(exchange, HttpStatus.OK, webLogAppender.entries(query.get("tracker"), parseAfter(query.get("after"))));
    }

    private static long parseAfter(final @Nullable String after) {
        if (after == null || after.isBlank()) {
            return 0L;
        }

        try {
            return Long.parseLong(after);
        } catch (final NumberFormatException e) {
            throw new HttpRequestException(HttpStatus.BAD_REQUEST, "Invalid 'after' value: '%s'".formatted(after), e);
        }
    }

    private static Map<String, String> parseQuery(final @Nullable String rawQuery) {
        final Map<String, String> query = new HashMap<>();
        if (rawQuery == null || rawQuery.isEmpty()) {
            return query;
        }

        for (final String parameter : rawQuery.split("&", -1)) {
            final int separator = parameter.indexOf('=');
            if (separator > 0) {
                query.put(URLDecoder.decode(parameter.substring(0, separator), StandardCharsets.UTF_8),
                    URLDecoder.decode(parameter.substring(separator + 1), StandardCharsets.UTF_8));
            }
        }
        return query;
    }
}
