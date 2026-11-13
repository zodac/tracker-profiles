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
import java.util.Map;
import java.util.Optional;
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
    private static final int NOT_FOUND = 404;
    private static final int METHOD_NOT_ALLOWED = 405;
    private static final int NO_RESPONSE_BODY = -1;
    private static final Map<String, String> CONTENT_TYPES = Map.of(
        "css", "text/css; charset=utf-8",
        "html", "text/html; charset=utf-8",
        "js", "text/javascript; charset=utf-8",
        "svg", "image/svg+xml"
    );

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

            // Only serve simple file names, preventing any traversal outside the resource directory
            if (path.contains("..") || path.contains("\\") || path.indexOf('/', 1) != -1) {
                exchange.sendResponseHeaders(NOT_FOUND, NO_RESPONSE_BODY);
                return;
            }

            final Optional<byte[]> resource = readResource(path);
            if (resource.isEmpty()) {
                LOGGER.trace("No web resource found for path '{}'", path);
                exchange.sendResponseHeaders(NOT_FOUND, NO_RESPONSE_BODY);
                return;
            }

            final byte[] content = resource.get();
            exchange.getResponseHeaders().set("Content-Type", contentType(path));
            exchange.getResponseHeaders().set("Cache-Control", "no-cache");
            exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
            exchange.getResponseHeaders().set("Content-Security-Policy", "default-src 'self'; frame-ancestors 'none'");
            exchange.sendResponseHeaders(OK, content.length);
            try (final OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(content);
            }
        }
    }

    private static Optional<byte[]> readResource(final String path) throws IOException {
        try (final InputStream inputStream = StaticResourceHandler.class.getResourceAsStream(RESOURCE_DIRECTORY + path)) {
            return inputStream == null ? Optional.empty() : Optional.of(inputStream.readAllBytes());
        }
    }

    private static String contentType(final String path) {
        final int extensionIndex = path.lastIndexOf('.');
        final String extension = extensionIndex == -1 ? "" : path.substring(extensionIndex + 1);
        return CONTENT_TYPES.getOrDefault(extension, "application/octet-stream");
    }
}
