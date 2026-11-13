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
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import net.zodac.tracker.web.log.LogEntry;
import net.zodac.tracker.web.log.WebLogAppender;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jspecify.annotations.Nullable;

/**
 * Streams application logs to the web UI using <a href="https://html.spec.whatwg.org/multipage/server-sent-events.html">Server-Sent Events</a>.
 * On connection, all retained log messages are sent (after the {@code Last-Event-ID}, if the browser is reconnecting), followed by each new log
 * message as it is written.
 */
public final class LogStreamHandler extends ApiHandler {

    /**
     * The context path for this handler.
     */
    public static final String CONTEXT_PATH = "/api/logs";

    private static final Logger LOGGER = LogManager.getLogger();
    private static final Duration KEEP_ALIVE_INTERVAL = Duration.ofSeconds(15L);
    private static final int MAXIMUM_QUEUED_ENTRIES = 10_000;
    private static final byte[] KEEP_ALIVE = ": keep-alive\n\n".getBytes(StandardCharsets.UTF_8);

    private final WebLogAppender webLogAppender;

    private LogStreamHandler(final WebLogAppender webLogAppender) {
        super(CONTEXT_PATH);
        this.webLogAppender = webLogAppender;
    }

    /**
     * Creates a {@link LogStreamHandler}.
     *
     * @param webLogAppender the {@link WebLogAppender} providing the log messages
     * @return the {@link LogStreamHandler}
     */
    public static LogStreamHandler create(final WebLogAppender webLogAppender) {
        return new LogStreamHandler(webLogAppender);
    }

    @Override
    protected void handleGet(final HttpExchange exchange, final @Nullable String pathParameter) throws IOException {
        final long lastEventId = parseLastEventId(exchange.getRequestHeaders().getFirst("Last-Event-ID"));

        // Entries are queued by the logging thread, and written by this thread, so a slow client never blocks logging
        // If the queue is full (client is not reading), new entries are dropped for that client only
        final BlockingQueue<LogEntry> queue = new LinkedBlockingQueue<>(MAXIMUM_QUEUED_ENTRIES);
        final Consumer<LogEntry> subscriber = queue::offer;
        final List<LogEntry> retainedEntries = webLogAppender.subscribe(subscriber);

        exchange.getResponseHeaders().set("Content-Type", "text/event-stream; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.getResponseHeaders().set("X-Accel-Buffering", "no");
        exchange.sendResponseHeaders(HttpStatus.OK.code(), 0);

        try (final OutputStream outputStream = exchange.getResponseBody()) {
            for (final LogEntry logEntry : retainedEntries) {
                if (logEntry.id() > lastEventId) {
                    writeEvent(outputStream, logEntry);
                }
            }
            outputStream.flush();

            while (!Thread.currentThread().isInterrupted()) {
                final LogEntry logEntry = queue.poll(KEEP_ALIVE_INTERVAL.toSeconds(), TimeUnit.SECONDS);
                if (logEntry == null) {
                    outputStream.write(KEEP_ALIVE);
                } else {
                    writeEvent(outputStream, logEntry);
                }
                outputStream.flush();
            }
        } catch (final IOException e) {
            LOGGER.trace("Log stream client disconnected", e);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.trace("Log stream interrupted", e);
        } finally {
            webLogAppender.unsubscribe(subscriber);
        }
    }

    private static void writeEvent(final OutputStream outputStream, final LogEntry logEntry) throws IOException {
        // JSON never contains a raw newline, so the payload is always a single 'data' line
        final String event = "id: " + logEntry.id() + "\ndata: " + toJson(logEntry) + "\n\n";
        outputStream.write(event.getBytes(StandardCharsets.UTF_8));
    }

    private static long parseLastEventId(final @Nullable String lastEventId) {
        if (lastEventId == null || lastEventId.isBlank()) {
            return 0L;
        }

        try {
            return Long.parseLong(lastEventId.trim());
        } catch (final NumberFormatException e) {
            LOGGER.trace("Invalid Last-Event-ID: '{}'", lastEventId, e);
            return 0L;
        }
    }
}
