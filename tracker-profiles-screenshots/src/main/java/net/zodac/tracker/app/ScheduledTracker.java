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

package net.zodac.tracker.app;

import net.zodac.tracker.framework.TrackerCredential;
import net.zodac.tracker.framework.TrackerType;

/**
 * A tracker to be executed, with the {@link TrackerType} of its handler.
 *
 * @param credential the {@link TrackerCredential} of the tracker
 * @param type       the {@link TrackerType} of the tracker
 */
public record ScheduledTracker(TrackerCredential credential, TrackerType type) {

    /**
     * The name of the tracker.
     *
     * @return the tracker name
     */
    public String name() {
        return credential.name();
    }
}
