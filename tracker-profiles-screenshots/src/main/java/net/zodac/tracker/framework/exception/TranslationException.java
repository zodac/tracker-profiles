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
 * Exception used to indicate that a non-English web page was unable to be translated correctly.
 */
public class TranslationException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 4960542091514855207L;

    /**
     * Constructor that takes a cause {@link Throwable}.
     *
     * @param cause the cause {@link Throwable}
     */
    public TranslationException(final Throwable cause) {
        super(cause);
    }
}
