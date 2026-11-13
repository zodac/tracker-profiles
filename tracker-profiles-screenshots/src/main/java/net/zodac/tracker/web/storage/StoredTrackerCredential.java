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

package net.zodac.tracker.web.storage;

import net.zodac.tracker.framework.TrackerCredential;

/**
 * A tracker's credentials as stored in the {@link ApplicationDatabase}.
 *
 * @param name     the tracker name
 * @param username the user's username or email
 * @param password the user's password (empty if not yet set)
 * @param enabled  whether the tracker should be executed
 */
public record StoredTrackerCredential(String name, String username, String password, boolean enabled) {

    /**
     * Whether a password has been saved for the tracker.
     *
     * @return {@code true} if the password is not blank
     */
    public boolean hasPassword() {
        return !password.isBlank();
    }

    /**
     * Converts to a {@link TrackerCredential} for execution.
     *
     * @return the {@link TrackerCredential}
     */
    public TrackerCredential toTrackerCredential() {
        return new TrackerCredential(name, username, password);
    }

    @Override
    public String toString() {
        return "StoredTrackerCredential[name=%s, username=%s, password=****, enabled=%s]".formatted(name, username, enabled);
    }
}
