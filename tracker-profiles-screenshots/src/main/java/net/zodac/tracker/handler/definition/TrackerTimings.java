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

package net.zodac.tracker.handler.definition;

import java.time.Duration;

/**
 * Defines the timing {@link Duration}s used during the screenshot workflow for a tracker. Provides sensible defaults that can be overridden per
 * {@link net.zodac.tracker.handler.AbstractTrackerHandler} implementation where the tracker requires more time.
 */
public interface TrackerTimings {

    /**
     * The maximum {@link Duration} for a click to complete its action.
     *
     * @return the maximum click resolution {@link Duration}
     */
    default Duration maximumClickResolutionDuration() {
        return Duration.ofSeconds(15L);
    }

    /**
     * The maximum {@link Duration} for a link to complete loading.
     *
     * @return the maximum load resolution {@link Duration}
     */
    default Duration maximumLinkResolutionDuration() {
        return Duration.ofMinutes(3L);
    }

    /**
     * The {@link Duration} to wait for a page load.
     *
     * @return the page load wait {@link Duration}
     */
    default Duration pageLoadDuration() {
        return Duration.ofSeconds(30L);
    }

    /**
     * The {@link Duration} to wait for a page/element transition.
     *
     * @return the transition wait {@link Duration}
     */
    default Duration pageTransitionsDuration() {
        return Duration.ofSeconds(1L);
    }
}
