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

/**
 * One of the latest screenshots of a tracker (a profile screenshot of its latest successful execution, or the failure screenshot of its latest
 * execution), as stored in the {@link ApplicationDatabase}.
 *
 * @param name     the tracker name
 * @param id       the ID of the screenshot within its tracker (see {@link net.zodac.tracker.web.screenshot.ScreenshotFile#id()})
 * @param position the position of the screenshot among those of the tracker, in the order they are shown
 * @param path     the absolute path of the screenshot file
 * @param savedAt  when the screenshot was saved, as epoch milliseconds
 */
public record StoredTrackerScreenshot(String name, String id, int position, String path, long savedAt) {

}
