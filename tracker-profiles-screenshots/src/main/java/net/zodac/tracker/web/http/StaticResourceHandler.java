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
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.zip.CRC32;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Serves the static files of the web UI from the {@code web/} directory on the classpath.
 */
public final class StaticResourceHandler implements HttpHandler {

    /**
     * The context path for this handler.
     */
    public static final String CONTEXT_PATH = "/";

    private static final Logger LOGGER = LogManager.getLogger();
    private static final String RESOURCE_DIRECTORY = "/web";
    private static final String INDEX_FILE = "/index.html";
    private static final int OK = 200;
    private static final int NOT_MODIFIED = 304;
    private static final int NOT_FOUND = 404;
    private static final int METHOD_NOT_ALLOWED = 405;
    private static final int NO_RESPONSE_BODY = -1;
    private static final Map<String, String> CONTENT_TYPES = Map.of(
        "css", "text/css; charset=utf-8",
        "html", "text/html; charset=utf-8",
        "js", "text/javascript; charset=utf-8",
        "mjs", "text/javascript; charset=utf-8",
        "svg", "image/svg+xml"
    );
    // A file in the resource directory, or in its 'js' directory (the JavaScript modules), preventing any traversal outside them
    private static final Pattern VALID_PATH = Pattern.compile("^/(?:js/)?[A-Za-z0-9_-]+\\.[a-z]+$");

    // The resources never change while the application is running, so each is only read (and its ETag computed) once
    private final Map<String, Optional<Resource>> resources = new ConcurrentHashMap<>();

    private StaticResourceHandler() {

    }

    /**
     * Creates a {@link StaticResourceHandler}.
     *
     * @return the {@link StaticResourceHandler}
     */
    public static StaticResourceHandler create() {
        return new StaticResourceHandler();
    }

    @Override
    public void handle(final HttpExchange exchange) throws IOException {
        try (exchange) {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(METHOD_NOT_ALLOWED, NO_RESPONSE_BODY);
                return;
            }

            final String requestPath = exchange.getRequestURI().getPath();
            final String path = "/".equals(requestPath) ? INDEX_FILE : requestPath;

            if (!VALID_PATH.matcher(path).matches()) {
                exchange.sendResponseHeaders(NOT_FOUND, NO_RESPONSE_BODY);
                return;
            }

            final Optional<Resource> resource = resources.computeIfAbsent(path, StaticResourceHandler::readResource);
            if (resource.isEmpty()) {
                LOGGER.trace("No web resource found for path '{}'", path);
                exchange.sendResponseHeaders(NOT_FOUND, NO_RESPONSE_BODY);
                return;
            }

            // Revalidated on each load (so a new version is used after an upgrade), but only sent again when it has changed
            final Resource content = resource.get();
            exchange.getResponseHeaders().set("Content-Type", contentType(path));
            exchange.getResponseHeaders().set("Cache-Control", "no-cache");
            exchange.getResponseHeaders().set("ETag", content.etag());
            exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
            exchange.getResponseHeaders().set("Content-Security-Policy", "default-src 'self'; frame-ancestors 'none'");
            if (content.etag().equals(exchange.getRequestHeaders().getFirst("If-None-Match"))) {
                exchange.sendResponseHeaders(NOT_MODIFIED, NO_RESPONSE_BODY);
                return;
            }

            exchange.sendResponseHeaders(OK, content.bytes().length);
            try (final OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(content.bytes());
            }
        }
    }

    private static Optional<Resource> readResource(final String path) {
        try (final InputStream inputStream = StaticResourceHandler.class.getResourceAsStream(RESOURCE_DIRECTORY + path)) {
            if (inputStream == null) {
                return Optional.empty();
            }

            final byte[] bytes = inputStream.readAllBytes();
            final CRC32 checksum = new CRC32();
            checksum.update(bytes);
            return Optional.of(new Resource(bytes, "\"%08x-%d\"".formatted(checksum.getValue(), bytes.length)));
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String contentType(final String path) {
        final int extensionIndex = path.lastIndexOf('.');
        final String extension = extensionIndex == -1 ? "" : path.substring(extensionIndex + 1);
        return CONTENT_TYPES.getOrDefault(extension, "application/octet-stream");
    }

    // The content of a resource, with its (quoted) entity tag
    private record Resource(byte[] bytes, String etag) {

    }
}
