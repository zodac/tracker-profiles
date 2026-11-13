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

package net.zodac.tracker.framework.cloudflare;

import org.jspecify.annotations.Nullable;

/**
 * The result of an attempt by the {@link CloudflareSolver} to pass a Cloudflare verification check.
 *
 * @param passed    whether the check was passed (or there was no check to pass)
 * @param errorCode the error code reported by Cloudflare if it rejected the attempt, or {@code null} if it was passed or no error was reported
 */
public record CloudflareResult(boolean passed, @Nullable String errorCode) {

    /**
     * The check was passed, or there was no check to pass.
     *
     * @return the {@link CloudflareResult}
     */
    public static CloudflareResult ofPassed() {
        return new CloudflareResult(true, null);
    }

    /**
     * The check was not passed in time, but Cloudflare did not report why.
     *
     * @return the {@link CloudflareResult}
     */
    public static CloudflareResult ofNotPassed() {
        return new CloudflareResult(false, null);
    }

    /**
     * Cloudflare explicitly rejected the attempt to pass the check.
     *
     * @param errorCode the error code reported by Cloudflare
     * @return the {@link CloudflareResult}
     */
    public static CloudflareResult ofRejected(final String errorCode) {
        return new CloudflareResult(false, errorCode);
    }

    /**
     * Whether Cloudflare explicitly rejected the attempt, rather than the attempt simply not completing in time.
     *
     * @return {@code true} if Cloudflare reported an error code
     */
    public boolean wasRejected() {
        return errorCode != null;
    }
}
