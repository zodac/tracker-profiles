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

package net.zodac.tracker.framework.progress;

/**
 * Enum defining the discrete steps taken when processing a tracker, reported to the web UI as each one completes.
 */
public enum TrackerStep {

    /**
     * Opening the tracker URL in the browser.
     */
    OPEN_TRACKER,

    /**
     * Logging in to the tracker.
     */
    LOGIN,

    /**
     * Navigating to the user profile page.
     */
    OPEN_PROFILE_PAGE,

    /**
     * Taking the screenshot(s) of the profile page.
     */
    TAKE_SCREENSHOTS,

    /**
     * Logging out of the tracker.
     */
    LOGOUT;

    /**
     * The number of {@link TrackerStep} values.
     */
    public static final int NUMBER_OF_STEPS = values().length;
}
