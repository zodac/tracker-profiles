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

package net.zodac.tracker.framework.view;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jspecify.annotations.Nullable;

/**
 * A live view of the tab of a tracker's browser, shown in the web UI, which also sends the user's input to the tab while the tracker is waiting
 * for one.
 *
 * <p>
 * The view connects to the tab with the Chrome DevTools Protocol (a WebSocket to the browser's DevTools endpoint), separately from
 * {@code chromedriver}, so it is updated even while the tracker is running a WebDriver command. The page is streamed as JPEG frames by
 * {@code Page.startScreencast}, and the input is sent with the {@code Input} domain, so the browser receives it as if from a real mouse and keyboard.
 *
 * <p>
 * The connection and screencast are started when a frame is first requested, and the screencast is stopped once no frame has been requested for
 * {@link #IDLE_TIMEOUT}, so the browsers that nobody watches do not encode frames. Each frame is acknowledged (which lets the browser send the next
 * one) after {@link #FRAME_INTERVAL}, which limits the frame rate.
 */
public final class BrowserView {

    private static final Logger LOGGER = LogManager.getLogger();
    private static final Gson GSON = new Gson();
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
        .executor(Executors.newVirtualThreadPerTaskExecutor())
        .build();

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5L);
    private static final Duration SEND_TIMEOUT = Duration.ofSeconds(5L);
    private static final Duration IDLE_TIMEOUT = Duration.ofSeconds(5L);
    private static final Duration FRAME_INTERVAL = Duration.ofMillis(100L);
    private static final int JPEG_QUALITY = 85;
    private static final int MAXIMUM_FRAME_WIDTH = 1920;
    private static final int MAXIMUM_FRAME_HEIGHT = 1200;
    // Shared by every view, so a tracker's new browser (such as on a retry) always has newer frames than the one the web UI last showed
    private static final AtomicLong FRAME_IDS = new AtomicLong();

    private final URI webSocketUri;
    private final Lock lock = new ReentrantLock();
    private final Condition frameReceived = lock.newCondition();

    // Only accessed while holding the lock
    private @Nullable WebSocket webSocket;
    private boolean screencasting;
    private boolean closed;
    private @Nullable BrowserFrame latestFrame;
    // Frames up to this ID were received before the screencast was last started, so may no longer show the page
    private long staleFrameId;
    private long nextCommandId = 1L;
    private long lastRequestedAt;

    private BrowserView(final URI webSocketUri) {
        this.webSocketUri = webSocketUri;
    }

    /**
     * Creates a {@link BrowserView} of a tab of a browser. It does not connect to the browser until a frame is requested.
     *
     * @param debuggerAddress the address of the browser's DevTools endpoint (such as {@code localhost:41233})
     * @param targetId        the DevTools target ID of the tab (its WebDriver window handle)
     * @return the {@link BrowserView}
     * @throws IllegalArgumentException thrown if the address or target ID is blank, or contains a {@code /}
     */
    public static BrowserView forTab(final String debuggerAddress, final String targetId) {
        if (debuggerAddress.isBlank() || debuggerAddress.contains("/")) {
            throw new IllegalArgumentException("Invalid DevTools address: '%s'".formatted(debuggerAddress));
        }
        if (targetId.isBlank() || targetId.contains("/")) {
            throw new IllegalArgumentException("Invalid DevTools target ID: '%s'".formatted(targetId));
        }
        return new BrowserView(URI.create("ws://%s/devtools/page/%s".formatted(debuggerAddress, targetId)));
    }

    /**
     * Waits for a frame newer than {@code afterId}, starting the screencast if needed. A frame received before the screencast was (re)started is not
     * returned, as the page may have changed since.
     *
     * @param afterId the ID of the last frame already shown ({@code 0} for none), so the latest frame is returned immediately if it is newer
     *                (frame IDs increase across every view)
     * @param timeout how long to wait for a newer frame
     * @return the latest {@link BrowserFrame}, or {@link Optional#empty()} if there is no newer frame in time, or the view is closed
     * @throws InterruptedException thrown if interrupted while waiting
     */
    public Optional<BrowserFrame> awaitFrame(final long afterId, final Duration timeout) throws InterruptedException {
        lock.lock();
        try {
            lastRequestedAt = System.currentTimeMillis();
            startScreencast();

            final long newerThanId = Math.max(afterId, staleFrameId);
            long remainingNanos = timeout.toNanos();
            while (!closed && (latestFrame == null || latestFrame.id() <= newerThanId)) {
                if (remainingNanos <= 0L) {
                    return Optional.empty();
                }
                remainingNanos = frameReceived.awaitNanos(remainingNanos);
            }
            return closed ? Optional.empty() : Optional.ofNullable(latestFrame);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Sends the user's input to the tab, in order. Every input is validated before any is sent.
     *
     * @param events the {@link BrowserInputEvent}s
     * @throws IllegalArgumentException thrown if an input is not valid
     * @throws IllegalStateException    thrown if the view is closed, or no frame has been shown yet (so the input cannot be positioned)
     * @throws InterruptedException     thrown if interrupted while sending the input
     */
    public void dispatch(final List<BrowserInputEvent> events) throws InterruptedException {
        lock.lock();
        try {
            final BrowserFrame frame = latestFrame;
            final WebSocket socket = webSocket;
            if (closed || socket == null || frame == null) {
                throw new IllegalStateException(closed ? "The browser is closed" : "The browser is not shown yet");
            }

            final List<DevToolsCommand> commands = events
                .stream()
                .map(event -> event.toCommand(frame.viewportWidth(), frame.viewportHeight()))
                .toList();
            for (final DevToolsCommand command : commands) {
                if (!send(socket, command)) {
                    throw new IllegalStateException("Unable to send the input to the browser");
                }
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Reloads the page in the tab, as the user would with the browser's reload button.
     *
     * @throws IllegalStateException thrown if the view is closed, or the browser cannot be reached
     * @throws InterruptedException  thrown if interrupted while sending the reload
     */
    public void reload() throws InterruptedException {
        lock.lock();
        try {
            if (closed) {
                throw new IllegalStateException("The browser is closed");
            }
            final WebSocket socket = connect();
            if (socket == null || !send(socket, new DevToolsCommand("Page.reload", Map.of()))) {
                throw new IllegalStateException("Unable to reload the page in the browser");
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Whether the view is closed, either by {@link #close()} or because the browser closed the connection.
     *
     * @return {@code true} if the view is closed
     */
    public boolean isClosed() {
        lock.lock();
        try {
            return closed;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Closes the view, ending any request waiting for a frame. The browser itself is not affected.
     */
    public void close() {
        lock.lock();
        try {
            closed = true;
            screencasting = false;
            frameReceived.signalAll();
            // A closed view never connects again, so the connection is not cleared
            if (webSocket != null) {
                webSocket.abort();
            }
        } finally {
            lock.unlock();
        }
    }

    // Must be called while holding the lock
    private void startScreencast() throws InterruptedException {
        if (closed || screencasting) {
            return;
        }

        final WebSocket socket = connect();
        if (socket == null) {
            return;
        }

        final Map<String, Object> params = new LinkedHashMap<>();
        params.put("format", "jpeg");
        params.put("quality", JPEG_QUALITY);
        params.put("maxWidth", MAXIMUM_FRAME_WIDTH);
        params.put("maxHeight", MAXIMUM_FRAME_HEIGHT);
        screencasting = send(socket, new DevToolsCommand("Page.startScreencast", params));
        if (screencasting && latestFrame != null) {
            staleFrameId = latestFrame.id();
        }
        LOGGER.trace("Started browser screencast: {}", screencasting);
    }

    // Must be called while holding the lock, and not once closed
    private @Nullable WebSocket connect() throws InterruptedException {
        if (webSocket != null) {
            return webSocket;
        }

        try {
            webSocket = HTTP_CLIENT.newWebSocketBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .buildAsync(webSocketUri, new DevToolsListener())
                .get(CONNECT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            LOGGER.trace("Connected to browser DevTools at '{}'", webSocketUri);
            return webSocket;
        } catch (final ExecutionException | TimeoutException e) {
            LOGGER.debug("Unable to connect to browser DevTools at '{}'", webSocketUri, e);
            return null;
        }
    }

    // Must be called while holding the lock. Each message is sent once the previous one has been sent, as a WebSocket requires
    private boolean send(final WebSocket socket, final DevToolsCommand command) throws InterruptedException {
        final Map<String, Object> message = new LinkedHashMap<>();
        message.put("id", nextCommandId);
        message.put("method", command.method());
        message.put("params", command.params());
        nextCommandId++;

        try {
            socket.sendText(GSON.toJson(message), true).get(SEND_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            return true;
        } catch (final ExecutionException | TimeoutException e) {
            LOGGER.debug("Unable to send '{}' to browser DevTools", command.method(), e);
            return false;
        }
    }

    private void handleMessage(final String text) {
        final JsonObject message;
        try {
            message = JsonParser.parseString(text).getAsJsonObject();
        } catch (final JsonParseException | IllegalStateException e) {
            LOGGER.trace("Ignoring invalid DevTools message", e);
            return;
        }

        if (message.has("error")) {
            LOGGER.trace("DevTools command #{} failed: {}", message.get("id"), message.get("error"));
            return;
        }
        if (!message.has("method") || !"Page.screencastFrame".equals(message.get("method").getAsString())) {
            return;
        }

        try {
            final JsonObject params = message.getAsJsonObject("params");
            final JsonObject metadata = params.getAsJsonObject("metadata");
            final byte[] jpeg = Base64.getDecoder().decode(params.get("data").getAsString());
            final int sessionId = params.get("sessionId").getAsInt();
            receiveFrame(jpeg, metadata.get("deviceWidth").getAsDouble(), metadata.get("deviceHeight").getAsDouble(), sessionId);
        } catch (final RuntimeException e) {
            LOGGER.trace("Ignoring invalid screencast frame", e);
        }
    }

    private void receiveFrame(final byte[] jpeg, final double viewportWidth, final double viewportHeight, final int sessionId) {
        lock.lock();
        try {
            if (closed) {
                return;
            }
            latestFrame = BrowserFrame.of(FRAME_IDS.incrementAndGet(), jpeg, viewportWidth, viewportHeight);
            frameReceived.signalAll();
        } finally {
            lock.unlock();
        }

        Thread.ofVirtual()
            .name("browser-view-acknowledgement")
            .start(() -> acknowledgeFrame(sessionId));
    }

    // The next frame is only sent once this one is acknowledged, so the screencast is stopped instead if nobody is watching
    private void acknowledgeFrame(final int sessionId) {
        try {
            Thread.sleep(FRAME_INTERVAL);
            lock.lock();
            try {
                final WebSocket socket = webSocket;
                if (closed || socket == null || !screencasting) {
                    return;
                }

                if (System.currentTimeMillis() - lastRequestedAt > IDLE_TIMEOUT.toMillis()) {
                    LOGGER.trace("No frame requested recently, stopping browser screencast");
                    screencasting = false;
                    send(socket, new DevToolsCommand("Page.stopScreencast", Map.of()));
                    return;
                }
                send(socket, new DevToolsCommand("Page.screencastFrameAck", Map.of("sessionId", sessionId)));
            } finally {
                lock.unlock();
            }
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.trace("Interrupted acknowledging screencast frame", e);
        }
    }

    // The browser has closed the connection (such as when it is quit), so nothing more can be shown
    private void disconnected() {
        lock.lock();
        try {
            closed = true;
            screencasting = false;
            frameReceived.signalAll();
        } finally {
            lock.unlock();
        }
    }

    // Messages are delivered one at a time, possibly split into parts
    private final class DevToolsListener implements WebSocket.Listener {

        private final List<String> messageParts = new ArrayList<>();

        @Override
        public @Nullable CompletionStage<?> onText(final WebSocket socket, final CharSequence data, final boolean last) {
            messageParts.add(data.toString());
            if (last) {
                final String text = String.join("", messageParts);
                messageParts.clear();
                handleMessage(text);
            }
            socket.request(1L);
            return null;
        }

        @Override
        public @Nullable CompletionStage<?> onClose(final WebSocket socket, final int statusCode, final String reason) {
            LOGGER.trace("Browser DevTools connection closed ({}: '{}')", statusCode, reason);
            disconnected();
            return null;
        }

        @Override
        public void onError(final WebSocket socket, final Throwable error) {
            LOGGER.trace("Browser DevTools connection failed", error);
            disconnected();
        }
    }
}
