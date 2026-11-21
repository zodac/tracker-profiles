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

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * The sets of screenshots that can be downloaded together from the Trackers tab of the web UI, from the latest screenshots of every tracker across
 * all executions: the profile screenshots of each tracker's latest success, and the failure screenshot of each tracker whose latest execution
 * failed.
 */
public enum ScreenshotDownload {

    /**
     * Every latest screenshot: the profile screenshots and the failure screenshots.
     */
    ALL("All"),

    /**
     * The profile screenshots, of every redaction type.
     */
    SUCCESSFUL("Successful"),

    /**
     * The profile screenshots with {@link net.zodac.tracker.framework.redactor.RedactionType#BLUR} redaction.
     */
    BLUR("Blur redaction"),

    /**
     * The profile screenshots with {@link net.zodac.tracker.framework.redactor.RedactionType#BOX} redaction.
     */
    BOX("Box redaction"),

    /**
     * The profile screenshots with no redaction ({@link net.zodac.tracker.framework.redactor.RedactionType#NONE}).
     */
    NONE("No redaction"),

    /**
     * The failure screenshots.
     */
    ERRORS("Errors");

    private final String label;

    ScreenshotDownload(final String label) {
        this.label = label;
    }

    /**
     * Finds the {@link ScreenshotDownload} for the value of the {@code type} query parameter (see {@link #parameter()}).
     *
     * @param parameter the value of the query parameter, which may be {@code null}
     * @return the {@link ScreenshotDownload}, or {@link Optional#empty()} if there is none for the value
     */
    public static Optional<ScreenshotDownload> fromParameter(final @Nullable String parameter) {
        return Arrays.stream(values())
            .filter(download -> download.parameter().equals(parameter))
            .findAny();
    }

    /**
     * The value of the {@code type} query parameter that requests this download.
     *
     * @return the lower-case name
     */
    public String parameter() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * The user-facing name of the download, displayed in the web UI.
     *
     * @return the label
     */
    public String label() {
        return label;
    }

    /**
     * Whether a screenshot is part of this download. The redaction downloads match the {@link ScreenshotFile#id()} of a profile screenshot, which
     * is the lower-case name of its redaction type.
     *
     * @param screenshot the {@link ScreenshotFile}
     * @return {@code true} if the screenshot is part of this download
     */
    public boolean includes(final ScreenshotFile screenshot) {
        return switch (this) {
            case ALL -> true;
            case SUCCESSFUL -> !screenshot.failure();
            case ERRORS -> screenshot.failure();
            case BLUR, BOX, NONE -> !screenshot.failure() && screenshot.id().equals(parameter());
        };
    }
}
