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
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.zodac.tracker.framework.view.BrowserFrame;
import net.zodac.tracker.framework.view.BrowserInputEvent;
import net.zodac.tracker.framework.view.BrowserView;
import net.zodac.tracker.framework.view.BrowserViews;
import net.zodac.tracker.web.RunManager;
import org.jspecify.annotations.Nullable;

/**
 * Serves the live view of a tracker's browser (see {@link BrowserView}), and sends the user's input to it.
 *
 * <ul>
 *     <li>{@code GET /api/browser?tracker={name}&after={id}}: the latest frame of the tracker's browser as a JPEG image (with its ID in the
 *     {@value #FRAME_ID_HEADER} header), once there is one newer than {@code after}, or {@code 204 No Content} if there is none within
 *     {@link #FRAME_WAIT}, so the web UI requests the next frame as soon as it has shown the previous one</li>
 *     <li>{@code POST /api/browser/input?tracker={name}}: sends the user's input (a {@link BrowserInput}) to the tracker's browser, only while the
 *     tracker is waiting for a user input</li>
 * </ul>
 *
 * <p>
 * Only the browsers of trackers in progress are listed by {@link BrowserViews}, so no other browser can be seen.
 */
public final class BrowserApiHandler extends ApiHandler {

    /**
     * The context path for this handler.
     */
    public static final String CONTEXT_PATH = "/api/browser";

    private static final String INPUT_PATH = "input";
    private static final String FRAME_ID_HEADER = "X-Frame-Id";
    private static final Duration FRAME_WAIT = Duration.ofSeconds(1L);
    private static final int MAXIMUM_EVENTS = 200;
    private static final int NO_RESPONSE_BODY = -1;

    private final RunManager runManager;

    private BrowserApiHandler(final RunManager runManager) {
        super(CONTEXT_PATH);
        this.runManager = runManager;
    }

    /**
     * Creates a {@link BrowserApiHandler}.
     *
     * @param runManager the {@link RunManager}, to check whether a tracker is waiting for a user input
     * @return the {@link BrowserApiHandler}
     */
    public static BrowserApiHandler create(final RunManager runManager) {
        return new BrowserApiHandler(runManager);
    }

    @Override
    protected void handleGet(final HttpExchange exchange, final @Nullable String pathParameter) throws IOException {
        if (pathParameter != null) {
            throw new HttpRequestException(HttpStatus.NOT_FOUND, "Unknown path: '%s'".formatted(pathParameter));
        }

        final Map<String, String> query = queryParameters(exchange);
        final String trackerName = requireTrackerName(query);
        final BrowserView view = findView(trackerName);
        final Optional<BrowserFrame> frame;
        try {
            frame = view.awaitFrame(parseFrameId(query.get("after")), FRAME_WAIT);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new HttpRequestException(HttpStatus.SERVICE_UNAVAILABLE, "Interrupted while waiting for the browser", e);
        }

        if (frame.isPresent()) {
            sendFrame(exchange, frame.get());
            return;
        }
        if (view.isClosed()) {
            throw noBrowser(trackerName);
        }
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(HttpStatus.NO_CONTENT.code(), NO_RESPONSE_BODY);
    }

    @Override
    protected void handlePost(final HttpExchange exchange, final @Nullable String pathParameter) throws IOException {
        if (!INPUT_PATH.equals(pathParameter)) {
            throw new HttpRequestException(HttpStatus.NOT_FOUND, "Unknown path: '%s'".formatted(pathParameter));
        }

        final String trackerName = requireTrackerName(queryParameters(exchange));
        final BrowserInput input = readJson(exchange, BrowserInput.class);
        final List<BrowserInputEvent> events = input.events();
        if (events == null || events.isEmpty() || events.size() > MAXIMUM_EVENTS) {
            throw new HttpRequestException(HttpStatus.BAD_REQUEST, "Between 1 and %d events must be sent".formatted(MAXIMUM_EVENTS));
        }
        if (!runManager.isWaitingForInput(trackerName)) {
            throw new HttpRequestException(HttpStatus.CONFLICT, "%s is not waiting for an input".formatted(trackerName));
        }

        final BrowserView view = findView(trackerName);
        try {
            view.dispatch(events);
        } catch (final IllegalArgumentException e) {
            throw new HttpRequestException(HttpStatus.BAD_REQUEST, Objects.requireNonNullElse(e.getMessage(), "Invalid input"), e);
        } catch (final IllegalStateException e) {
            throw new HttpRequestException(HttpStatus.CONFLICT, Objects.requireNonNullElse(e.getMessage(), "The browser is not shown"), e);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new HttpRequestException(HttpStatus.SERVICE_UNAVAILABLE, "Interrupted while sending the input to the browser", e);
        }
        exchange.sendResponseHeaders(HttpStatus.NO_CONTENT.code(), NO_RESPONSE_BODY);
    }

    private static String requireTrackerName(final Map<String, String> query) {
        final String trackerName = query.get("tracker");
        if (trackerName == null || trackerName.isBlank()) {
            throw new HttpRequestException(HttpStatus.BAD_REQUEST, "A tracker must be provided");
        }
        return trackerName;
    }

    private static BrowserView findView(final String trackerName) {
        return BrowserViews.find(trackerName).orElseThrow(() -> noBrowser(trackerName));
    }

    private static HttpRequestException noBrowser(final String trackerName) {
        return new HttpRequestException(HttpStatus.NOT_FOUND, "No browser is open for %s".formatted(trackerName));
    }

    private static long parseFrameId(final @Nullable String value) {
        if (value == null) {
            return 0L;
        }
        try {
            return Long.parseLong(value);
        } catch (final NumberFormatException e) {
            throw new HttpRequestException(HttpStatus.BAD_REQUEST, "Invalid frame ID: '%s'".formatted(value), e);
        }
    }

    private static void sendFrame(final HttpExchange exchange, final BrowserFrame frame) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "image/jpeg");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.getResponseHeaders().set(FRAME_ID_HEADER, String.valueOf(frame.id()));
        exchange.sendResponseHeaders(HttpStatus.OK.code(), frame.size());
        try (final OutputStream outputStream = exchange.getResponseBody()) {
            frame.writeTo(outputStream);
        }
    }

    /**
     * The request body to send the user's input to a tracker's browser.
     *
     * @param events the {@link BrowserInputEvent}s, in the order they happened
     */
    record BrowserInput(@Nullable List<BrowserInputEvent> events) {

    }
}
