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
import net.zodac.tracker.framework.driver.BrowserResolution;
import net.zodac.tracker.framework.input.UserInputResponse;
import net.zodac.tracker.web.RunManager;
import org.jspecify.annotations.Nullable;

/**
 * Handles requests to start an execution ({@code POST /api/run}), start an execution of only the trackers that failed in the previous execution
 * ({@code POST /api/run/failed}), both with the optional body {@code {"screenWidth": 1920, "screenHeight": 1080}} for the resolution of the browsers
 * with a UI, cancel the running execution ({@code POST /api/run/cancel}) or a single running tracker
 * ({@code POST /api/run/cancel/{tracker}}), respond to a request for a user input ({@code POST /api/run/input/{id}/continue} or
 * {@code .../skip}), and retrieve the status of the execution ({@code GET /api/run}).
 */
public final class RunApiHandler extends ApiHandler {

    /**
     * The context path for this handler.
     */
    public static final String CONTEXT_PATH = "/api/run";

    private static final String CANCEL_PATH = "cancel";
    private static final String CANCEL_TRACKER_PATH_PREFIX = "cancel/";
    private static final String FAILED_PATH = "failed";
    private static final String INPUT_PATH_PREFIX = "input/";
    private static final int INPUT_PATH_PARTS = 2;

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

        if (pathParameter != null && pathParameter.startsWith(CANCEL_TRACKER_PATH_PREFIX)) {
            final String trackerName = pathParameter.substring(CANCEL_TRACKER_PATH_PREFIX.length());
            if (!runManager.cancelTracker(trackerName)) {
                throw new HttpRequestException(HttpStatus.CONFLICT, "Tracker '%s' is not running".formatted(trackerName));
            }
            sendJson(exchange, HttpStatus.ACCEPTED, runManager.status());
            return;
        }

        if (FAILED_PATH.equals(pathParameter)) {
            if (!runManager.startFailedTrackers(readUiBrowserResolution(exchange))) {
                final String message = runManager.isExecutionInProgress()
                    ? "An execution is already in progress"
                    : "No tracker failed in the previous execution";
                throw new HttpRequestException(HttpStatus.CONFLICT, message);
            }
            sendJson(exchange, HttpStatus.ACCEPTED, runManager.status());
            return;
        }

        if (pathParameter != null && pathParameter.startsWith(INPUT_PATH_PREFIX)) {
            respondToInput(pathParameter.substring(INPUT_PATH_PREFIX.length()));
            sendJson(exchange, HttpStatus.OK, runManager.status());
            return;
        }

        if (pathParameter != null) {
            throw new HttpRequestException(HttpStatus.NOT_FOUND, "Unknown action: '%s'".formatted(pathParameter));
        }

        if (!runManager.start(readUiBrowserResolution(exchange))) {
            throw new HttpRequestException(HttpStatus.CONFLICT, "An execution is already in progress");
        }
        sendJson(exchange, HttpStatus.ACCEPTED, runManager.status());
    }

    // Browsers with a UI are shown in the web UI, so they use the resolution of its screen (when sent, as the request body is optional)
    private static BrowserResolution readUiBrowserResolution(final HttpExchange exchange) throws IOException {
        final StartRequest startRequest = readOptionalJson(exchange, StartRequest.class);
        return startRequest == null
            ? BrowserResolution.DEFAULT
            : BrowserResolution.fromScreen(startRequest.screenWidth(), startRequest.screenHeight());
    }

    // Path is '{id}/{action}'
    private void respondToInput(final String inputPath) {
        final String[] parts = inputPath.split("/", -1);
        if (parts.length != INPUT_PATH_PARTS) {
            throw new HttpRequestException(HttpStatus.NOT_FOUND, "Unknown input action: '%s'".formatted(inputPath));
        }

        final long inputId;
        try {
            inputId = Long.parseLong(parts[0]);
        } catch (final NumberFormatException e) {
            throw new HttpRequestException(HttpStatus.BAD_REQUEST, "Invalid input ID: '%s'".formatted(parts[0]), e);
        }

        final boolean waiting = switch (parts[1]) {
            case "continue" -> runManager.respondToInput(inputId, UserInputResponse.CONTINUE);
            case "skip" -> runManager.respondToInput(inputId, UserInputResponse.SKIP);
            default -> throw new HttpRequestException(HttpStatus.NOT_FOUND, "Unknown input action: '%s'".formatted(parts[1]));
        };

        if (!waiting) {
            throw new HttpRequestException(HttpStatus.CONFLICT, "The tracker is no longer waiting for this input");
        }
    }

    /**
     * The optional request body to start an execution.
     *
     * @param screenWidth  the width of the screen of the web UI, in pixels
     * @param screenHeight the height of the screen of the web UI, in pixels
     */
    record StartRequest(@Nullable Integer screenWidth, @Nullable Integer screenHeight) {

    }
}
