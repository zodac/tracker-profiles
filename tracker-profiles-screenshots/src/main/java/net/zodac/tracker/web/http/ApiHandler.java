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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jspecify.annotations.Nullable;

/**
 * Base {@link HttpHandler} for the web UI's JSON API. Dispatches requests by HTTP method, converts thrown {@link HttpRequestException}s into
 * error responses, and provides helpers to read and write JSON.
 *
 * <p>
 * Requests with a body ({@code POST}/{@code PUT}) must send a {@code Content-Type} of {@code application/json}. Since browsers cannot send these
 * cross-origin without a CORS preflight (which is never approved), this prevents other websites from triggering actions through the user's
 * browser. The same applies to {@code DELETE}, which cannot be sent cross-origin without a preflight.
 */
public abstract class ApiHandler implements HttpHandler {

    private static final Logger LOGGER = LogManager.getLogger();
    private static final Gson GSON = new GsonBuilder().serializeNulls().create();
    private static final int NO_RESPONSE_BODY = -1;
    private static final String JSON_CONTENT_TYPE = "application/json";

    private final String contextPath;

    /**
     * Constructor for {@link ApiHandler}.
     *
     * @param contextPath the context path the handler is registered under, used to extract any path parameter from the request URI
     */
    protected ApiHandler(final String contextPath) {
        this.contextPath = contextPath;
    }

    @Override
    public final void handle(final HttpExchange exchange) {
        try (exchange) {
            handleSafely(exchange);
        }
    }

    private void handleSafely(final HttpExchange exchange) {
        try {
            dispatch(exchange);
        } catch (final HttpRequestException e) {
            LOGGER.trace("Request to '{}' failed with status {}", exchange.getRequestURI(), e.httpStatus(), e);
            sendError(exchange, e.httpStatus(), e.getMessage());
        } catch (final IOException | SQLException | RuntimeException e) {
            LOGGER.debug("Unexpected error handling request to '{}'", exchange.getRequestURI(), e);
            LOGGER.warn("Unexpected error handling request to '{}': {}", exchange.getRequestURI(), e.getMessage());
            sendError(exchange, HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error: %s".formatted(e.getMessage()));
        }
    }

    private void dispatch(final HttpExchange exchange) throws IOException, SQLException {
        final String method = exchange.getRequestMethod().toUpperCase(Locale.ROOT);
        final String pathParameter = pathParameter(exchange);
        switch (method) {
            case "GET" -> handleGet(exchange, pathParameter);
            case "POST" -> {
                requireJsonContentType(exchange);
                handlePost(exchange, pathParameter);
            }
            case "PUT" -> {
                requireJsonContentType(exchange);
                handlePut(exchange, pathParameter);
            }
            case "DELETE" -> handleDelete(exchange, pathParameter);
            default -> sendError(exchange, HttpStatus.METHOD_NOT_ALLOWED, "Method not allowed");
        }
    }

    /**
     * Handles a {@code GET} request.
     *
     * @param exchange      the {@link HttpExchange}
     * @param pathParameter the decoded path following the context path, or {@code null} if there is none
     * @throws IOException  thrown if the request cannot be read or the response cannot be written
     * @throws SQLException thrown if the database cannot be accessed
     */
    protected void handleGet(final HttpExchange exchange, final @Nullable String pathParameter) throws IOException, SQLException {
        throw methodNotAllowed();
    }

    /**
     * Handles a {@code POST} request.
     *
     * @param exchange      the {@link HttpExchange}
     * @param pathParameter the decoded path following the context path, or {@code null} if there is none
     * @throws IOException  thrown if the request cannot be read or the response cannot be written
     * @throws SQLException thrown if the database cannot be accessed
     */
    protected void handlePost(final HttpExchange exchange, final @Nullable String pathParameter) throws IOException, SQLException {
        throw methodNotAllowed();
    }

    /**
     * Handles a {@code PUT} request.
     *
     * @param exchange      the {@link HttpExchange}
     * @param pathParameter the decoded path following the context path, or {@code null} if there is none
     * @throws IOException  thrown if the request cannot be read or the response cannot be written
     * @throws SQLException thrown if the database cannot be accessed
     */
    protected void handlePut(final HttpExchange exchange, final @Nullable String pathParameter) throws IOException, SQLException {
        throw methodNotAllowed();
    }

    /**
     * Handles a {@code DELETE} request.
     *
     * @param exchange      the {@link HttpExchange}
     * @param pathParameter the decoded path following the context path, or {@code null} if there is none
     * @throws IOException  thrown if the request cannot be read or the response cannot be written
     * @throws SQLException thrown if the database cannot be accessed
     */
    protected void handleDelete(final HttpExchange exchange, final @Nullable String pathParameter) throws IOException, SQLException {
        throw methodNotAllowed();
    }

    /**
     * Reads the request body as JSON.
     *
     * @param exchange the {@link HttpExchange}
     * @param type     the {@link Class} to convert the JSON into
     * @param <T>      the type of the request body
     * @return the converted request body
     * @throws IOException          thrown if the request body cannot be read
     * @throws HttpRequestException thrown if the request body is empty or invalid JSON
     */
    protected static <T> T readJson(final HttpExchange exchange, final Class<T> type) throws IOException {
        try (final InputStream inputStream = exchange.getRequestBody()) {
            final String body = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            final T result = GSON.fromJson(body, type);
            if (result == null) {
                throw new HttpRequestException(HttpStatus.BAD_REQUEST, "Request body must not be empty");
            }
            return result;
        } catch (final JsonParseException e) {
            throw new HttpRequestException(HttpStatus.BAD_REQUEST, "Request body is not valid JSON", e);
        }
    }

    /**
     * Sends a JSON response.
     *
     * @param exchange   the {@link HttpExchange}
     * @param httpStatus the {@link HttpStatus}
     * @param body       the object to be converted to JSON
     * @throws IOException thrown if the response cannot be written
     */
    protected static void sendJson(final HttpExchange exchange, final HttpStatus httpStatus, final Object body) throws IOException {
        final byte[] responseBytes = GSON.toJson(body).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", JSON_CONTENT_TYPE + "; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(httpStatus.code(), responseBytes.length == 0 ? NO_RESPONSE_BODY : responseBytes.length);
        try (final OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(responseBytes);
        }
    }

    /**
     * Converts an object to JSON.
     *
     * @param body the object to convert
     * @return the JSON {@link String}
     */
    protected static String toJson(final Object body) {
        return GSON.toJson(body);
    }

    /**
     * Reads the (decoded) query parameters of the request. If a parameter is repeated, the last value is used.
     *
     * @param exchange the {@link HttpExchange}
     * @return the query parameters, keyed by name
     */
    protected static Map<String, String> queryParameters(final HttpExchange exchange) {
        return parseQuery(exchange.getRequestURI().getRawQuery());
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

    private static void sendError(final HttpExchange exchange, final HttpStatus httpStatus, final @Nullable String message) {
        try {
            sendJson(exchange, httpStatus, Map.of("error", message == null ? "Unknown error" : message));
        } catch (final IOException e) {
            LOGGER.trace("Unable to send error response", e);
        }
    }

    private static void requireJsonContentType(final HttpExchange exchange) {
        final String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
        if (contentType == null || !contentType.toLowerCase(Locale.ROOT).startsWith(JSON_CONTENT_TYPE)) {
            throw new HttpRequestException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Content-Type must be '%s'".formatted(JSON_CONTENT_TYPE));
        }
    }

    private @Nullable String pathParameter(final HttpExchange exchange) {
        final String path = exchange.getRequestURI().getRawPath();
        if (path.length() <= contextPath.length() + 1) {
            return null;
        }
        return URLDecoder.decode(path.substring(contextPath.length() + 1), StandardCharsets.UTF_8);
    }

    private static HttpRequestException methodNotAllowed() {
        return new HttpRequestException(HttpStatus.METHOD_NOT_ALLOWED, "Method not allowed");
    }
}
