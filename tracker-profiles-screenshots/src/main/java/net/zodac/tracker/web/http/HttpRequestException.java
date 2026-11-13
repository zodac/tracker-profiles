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

import java.io.Serial;
import java.util.Objects;

/**
 * Thrown when a web UI request cannot be fulfilled, defining the {@link HttpStatus} and message to return to the client.
 */
public class HttpRequestException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 4118259771384610221L;

    /**
     * The {@link HttpStatus} to return to the client.
     *
     * @serial the {@link HttpStatus}
     */
    private final HttpStatus httpStatus;

    /**
     * Constructor that takes the {@link HttpStatus} and an error message.
     *
     * @param httpStatus the {@link HttpStatus}
     * @param message    the error message
     */
    public HttpRequestException(final HttpStatus httpStatus, final String message) {
        super(message);
        this.httpStatus = httpStatus;
    }

    /**
     * Constructor that takes the {@link HttpStatus}, an error message, and the cause.
     *
     * @param httpStatus the {@link HttpStatus}
     * @param message    the error message
     * @param cause      the cause of the error
     */
    public HttpRequestException(final HttpStatus httpStatus, final String message, final Throwable cause) {
        super(message, cause);
        this.httpStatus = httpStatus;
    }

    /**
     * Constructor that takes the {@link HttpStatus} and the cause, whose message is returned to the client.
     *
     * @param httpStatus the {@link HttpStatus}
     * @param cause      the cause of the error
     */
    public HttpRequestException(final HttpStatus httpStatus, final Throwable cause) {
        super(Objects.requireNonNullElse(cause.getMessage(), "Invalid request"), cause);
        this.httpStatus = httpStatus;
    }

    /**
     * The {@link HttpStatus} to return to the client.
     *
     * @return the {@link HttpStatus}
     */
    public HttpStatus httpStatus() {
        return httpStatus;
    }
}
