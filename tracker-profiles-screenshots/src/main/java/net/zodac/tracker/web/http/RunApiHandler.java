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
import net.zodac.tracker.web.RunManager;
import org.jspecify.annotations.Nullable;

/**
 * Handles requests to start an execution ({@code POST /api/run}), cancel the running execution ({@code POST /api/run/cancel}), and retrieve the
 * status of the execution ({@code GET /api/run}).
 */
public final class RunApiHandler extends ApiHandler {

    /**
     * The context path for this handler.
     */
    public static final String CONTEXT_PATH = "/api/run";

    private static final String CANCEL_PATH = "cancel";

    private final RunManager runManager;

    private RunApiHandler(final RunManager runManager) {
        super(CONTEXT_PATH);
        this.runManager = runManager;
    }

    /**
     * Creates a {@link RunApiHandler}.
     *
     * @param runManager the {@link RunManager} used to start executions
     * @return the {@link RunApiHandler}
     */
    public static RunApiHandler create(final RunManager runManager) {
        return new RunApiHandler(runManager);
    }

    @Override
    protected void handleGet(final HttpExchange exchange, final @Nullable String pathParameter) throws IOException {
        sendJson(exchange, HttpStatus.OK, runManager.status());
    }

    @Override
    protected void handlePost(final HttpExchange exchange, final @Nullable String pathParameter) throws IOException {
        if (CANCEL_PATH.equals(pathParameter)) {
            if (!runManager.cancel()) {
                throw new HttpRequestException(HttpStatus.CONFLICT, "No execution is in progress");
            }
            sendJson(exchange, HttpStatus.ACCEPTED, runManager.status());
            return;
        }

        if (pathParameter != null) {
            throw new HttpRequestException(HttpStatus.NOT_FOUND, "Unknown action: '%s'".formatted(pathParameter));
        }

        if (!runManager.start()) {
            throw new HttpRequestException(HttpStatus.CONFLICT, "An execution is already in progress");
        }
        sendJson(exchange, HttpStatus.ACCEPTED, runManager.status());
    }
}
