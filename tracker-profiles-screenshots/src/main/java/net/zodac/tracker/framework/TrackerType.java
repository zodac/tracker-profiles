/*
 * BSD Zero Clause License
 *
 * Copyright (c) 2021-2025 zodac.net
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

package net.zodac.tracker.framework;

/**
 * Enum defining the different types of trackers. This can be used to perform different executions for different trackers.
 */
public enum TrackerType {

    /**
     * The tracker can be run in headless mode (with no UI).
     */
    HEADLESS,

    /**
     * The tracker requires some manual input (like a Captcha or 2FA login), requiring a browser with the UI enabled.
     */
    MANUAL_INPUT_NEEDED,

    /**
     * The tracker is not in English and can be manually translated, but this requires a browser with the UI enabled.
     */
    NON_ENGLISH
}
