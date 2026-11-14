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

package net.zodac.tracker.framework.exception;

import java.io.Serial;

/**
 * Exception used to indicate an error with the content of an imported CSV file.
 */
public class InvalidCsvInputException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = -426556559835711779L;

    /**
     * Constructor that takes an error message.
     *
     * @param message the error message
     */
    public InvalidCsvInputException(final String message) {
        super(message);
    }

    /**
     * Constructor that takes an error message and the cause.
     *
     * @param message the error message
     * @param cause   the cause {@link Throwable}
     */
    public InvalidCsvInputException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
