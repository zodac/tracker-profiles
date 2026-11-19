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

/**
 * The HTTP status codes returned by the web UI.
 */
public enum HttpStatus {

    /**
     * The request was successful.
     */
    OK(200),

    /**
     * The request was successful, and started an asynchronous action.
     */
    ACCEPTED(202),

    /**
     * The request was successful, and there is nothing to return.
     */
    NO_CONTENT(204),

    /**
     * The request was invalid.
     */
    BAD_REQUEST(400),

    /**
     * The requested resource does not exist.
     */
    NOT_FOUND(404),

    /**
     * The HTTP method is not supported for the requested resource.
     */
    METHOD_NOT_ALLOWED(405),

    /**
     * The request conflicts with the current state of the application.
     */
    CONFLICT(409),

    /**
     * The request body has an unsupported content type.
     */
    UNSUPPORTED_MEDIA_TYPE(415),

    /**
     * An unexpected error occurred.
     */
    INTERNAL_SERVER_ERROR(500),

    /**
     * The request could not be handled at this time.
     */
    SERVICE_UNAVAILABLE(503);

    private final int code;

    HttpStatus(final int code) {
        this.code = code;
    }

    /**
     * The numeric HTTP status code.
     *
     * @return the HTTP status code
     */
    public int code() {
        return code;
    }
}
