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
import java.time.Duration;

/**
 * Exception used to indicate that writing a screenshot to disk had an error.
 */
public class ScreenshotWriteError extends RuntimeException {

    @Serial
    private static final long serialVersionUID = -4567630877374812049L;

    /**
     * Takes the {@link Duration} and constructs an error message for the {@link ScreenshotWriteError}.
     *
     * @param cause the cause {@link Throwable}
     */
    public ScreenshotWriteError(final String screenshotName, final Throwable cause) {
        super("Error writing screenshot '%s' to disk".formatted(screenshotName), cause);
    }
}
