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
import net.zodac.tracker.framework.cloudflare.CloudflareResult;

/**
 * Exception used to indicate that a Cloudflare verification check could not be passed automatically.
 */
public class CloudflareVerificationException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 4127708936215580317L;

    /**
     * Constructs an error message for the {@link CloudflareVerificationException}.
     *
     * @param trackerName the name of the tracker
     * @param result      the {@link CloudflareResult} of the failed attempt to pass the check
     */
    public CloudflareVerificationException(final String trackerName, final CloudflareResult result) {
        super(result.wasRejected()
            ? "Cloudflare rejected the automatic verification check for '%s' (error code %s)".formatted(trackerName, result.errorCode())
            : "Unable to automatically pass the Cloudflare verification check for '%s'".formatted(trackerName));
    }
}
