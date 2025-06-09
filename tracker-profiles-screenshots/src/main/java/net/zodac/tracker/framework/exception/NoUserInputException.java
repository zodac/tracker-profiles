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
import java.time.Duration;

/**
 * Exception used to indicate that a manual tracker has not received a user input in the specified time.
 */
public class NoUserInputException extends RuntimeException {

    private static final String ERROR_MESSAGE_FORMAT = "No user input received within %02d seconds";

    @Serial
    private static final long serialVersionUID = 3549095095563630059L;

    /**
     * Takes the {@link Duration} and constructs an error message for the {@link NoUserInputException}.
     *
     * @param timeout the {@link Duration} we waiting for the user input
     * @param cause   the cause {@link Throwable}
     */
    public NoUserInputException(final Duration timeout, final Throwable cause) {
        super(String.format(ERROR_MESSAGE_FORMAT, timeout.getSeconds()), cause);
    }
}
