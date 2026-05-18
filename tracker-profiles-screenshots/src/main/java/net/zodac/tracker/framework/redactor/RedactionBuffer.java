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

package net.zodac.tracker.framework.redactor;

/**
 * Defines the pixel buffers applied to each side of redaction when performing redaction. A positive value expands the redaction beyond the
 * {@link org.openqa.selenium.WebElement}'s content in that direction, while a negative value contracts it.
 *
 * @param up    pixels to extend the redaction above the element
 * @param down  pixels to extend the redaction below the element
 * @param left  pixels to extend the redaction to the left of the element
 * @param right pixels to extend the redaction to the right of the element
 */
public record RedactionBuffer(int up, int down, int left, int right) {

    /**
     * Default {@link RedactionBuffer} used when no custom buffer is required.
     */
    public static final RedactionBuffer DEFAULT = new RedactionBuffer(0, 0, 0, 0);

    /**
     * Static constructor to create an {@link RedactionBuffer}.
     *
     * @param up    pixels to extend the redaction above the element
     * @param down  pixels to extend the redaction below the element
     * @param left  pixels to extend the redaction to the left of the element
     * @param right pixels to extend the redaction to the right of the element
     * @return the created {@link RedactionBuffer}
     */
    public static RedactionBuffer of(final int up, final int down, final int left, final int right) {
        return new RedactionBuffer(up, down, left, right);
    }

    /**
     * Static constructor to create an {@link RedactionBuffer} with only a left offset.
     *
     * @param leftOffset pixels to extend the redaction to the left of the element
     * @return the created {@link RedactionBuffer}
     */
    public static RedactionBuffer withLeftOffset(final int leftOffset) {
        return of(0, 0, leftOffset, 0);
    }

    /**
     * Static constructor to create an {@link RedactionBuffer} with only a right offset.
     *
     * @param rightOffset pixels to extend the redaction to the right of the element
     * @return the created {@link RedactionBuffer}
     */
    public static RedactionBuffer withRightOffset(final int rightOffset) {
        return of(0, 0, 0, rightOffset);
    }
}
