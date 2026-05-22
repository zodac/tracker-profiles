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
import java.util.Collection;

/**
 * Exception used to indicate that a tracker is unavailable or cannot be loaded.
 */
public class TrackerUnavailableException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 8658837127489147931L;

    /**
     * Constructs an error message for the {@link TrackerUnavailableException}.
     *
     * @param trackerName the name of the tracker
     * @param urls        the URLs to access the tracker
     */
    public TrackerUnavailableException(final String trackerName, final Collection<String> urls) {
        super("Tracker unavailable, unable to connect to any URL for '%s': %s".formatted(trackerName, urls));
    }
}
