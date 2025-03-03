/*
 * BSD Zero Clause License
 *
 * Copyright (c) 2021-2025 zodac.net
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
 * Exception used to indicate that a manual tracker has not received a user input due to the use closing the dialog box.
 */
public class CancelledInputException extends RuntimeException {

    private static final String ERROR_MESSAGE = "User cancelled manual input";

    @Serial
    private static final long serialVersionUID = -4567630877374812049L;

    /**
     * Creates a {@link CancelledInputException} with the standard {@value #ERROR_MESSAGE}.
     */
    public CancelledInputException() {
        super(ERROR_MESSAGE);
    }
}
