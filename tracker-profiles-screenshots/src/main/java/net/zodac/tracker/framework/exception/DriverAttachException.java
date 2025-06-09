/*
 * BSD Zero Clause License
 *
 * Copyright (c) 2024-2025 zodac.net
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
 * Exception used to indicate that there was an issue attaching a driver to a Python-based Selenium web browser.
 */
public class DriverAttachException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = -5003869653465975980L;

    /**
     * Constructor that takes an error message.
     *
     * @param message the error message
     */
    public DriverAttachException(final String message) {
        super(message);
    }

    /**
     * Constructor that takes a cause {@link Throwable}.
     *
     * @param cause the cause {@link Throwable}
     */
    public DriverAttachException(final Throwable cause) {
        super("Unable to attach driver to Python Selenium web browser", cause);
    }
}
