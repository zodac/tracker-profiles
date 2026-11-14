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

package net.zodac.tracker.web.screenshot;

import java.nio.file.Path;
import java.util.Locale;
import net.zodac.tracker.framework.redactor.RedactionType;

/**
 * A screenshot taken for a tracker in the current (or most recent) execution, which can be viewed and downloaded in the web UI.
 *
 * @param id      the ID of the screenshot within its tracker: the lower-case {@link RedactionType} name, or {@link #FAILURE_ID}
 * @param label   the label shown for the screenshot
 * @param path    the screenshot file
 * @param failure whether this is a failure screenshot, rather than a profile screenshot
 * @param savedAt when the screenshot was saved (or found to exist), as epoch milliseconds
 */
public record ScreenshotFile(String id, String label, Path path, boolean failure, long savedAt) {

    /**
     * The {@link #id()} of a failure screenshot.
     */
    public static final String FAILURE_ID = "failure";

    /**
     * Creates a {@link ScreenshotFile} for a profile screenshot.
     *
     * @param redactionType the {@link RedactionType} of the screenshot
     * @param path          the screenshot file
     * @param savedAt       when the screenshot was saved, as epoch milliseconds
     * @return the {@link ScreenshotFile}
     */
    public static ScreenshotFile ofProfile(final RedactionType redactionType, final Path path, final long savedAt) {
        final String label = redactionType == RedactionType.NONE ? "No redaction" : "%s redaction".formatted(redactionType.formattedName());
        return new ScreenshotFile(redactionType.name().toLowerCase(Locale.ROOT), label, path, false, savedAt);
    }

    /**
     * Creates a {@link ScreenshotFile} for a failure screenshot.
     *
     * @param path    the failure screenshot file
     * @param savedAt when the screenshot was saved, as epoch milliseconds
     * @return the {@link ScreenshotFile}
     */
    public static ScreenshotFile ofFailure(final Path path, final long savedAt) {
        return new ScreenshotFile(FAILURE_ID, "Failure", path, true, savedAt);
    }

    /**
     * The name of the screenshot file.
     *
     * @return the file name
     */
    public String fileName() {
        return String.valueOf(path.getFileName());
    }
}
