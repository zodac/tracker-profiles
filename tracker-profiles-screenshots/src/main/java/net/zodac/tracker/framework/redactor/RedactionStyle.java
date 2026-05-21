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
 * Visual parameters for a single JavaScript redaction call, bundling the box colours, label text, blur definition, and JS mode string into one value.
 *
 * @param bgColor       the background colour of the redaction overlay (empty for blur mode)
 * @param textColor     the text colour of the redaction overlay label (empty for blur mode)
 * @param label         the label text shown on the redaction overlay (empty for blur mode)
 * @param blurDef       the CSS filter value applied to the element (empty for box mode)
 * @param mode          the JS-side mode string passed to the redaction scripts: {@code "box"} or {@code "blur"}
 */
record RedactionStyle(String bgColor, String textColor, String label, String blurDef, String mode) {

    /**
     * Creates a {@link RedactionStyle} for blur-mode redaction. Colours and label are intentionally empty; the blur filter is applied instead.
     *
     * @return a blur {@link RedactionStyle}
     */
    static RedactionStyle blur() {
        return new RedactionStyle("", "", "", "blur(0.5em)", "blur");
    }

    /**
     * Creates a {@link RedactionStyle} for box-mode redaction with the given colours and label.
     *
     * @param bgColor   the background colour of the overlay
     * @param textColor the text colour of the overlay label
     * @param label     the label shown inside the overlay
     * @return a box {@link RedactionStyle}
     */
    static RedactionStyle box(final String bgColor, final String textColor, final String label) {
        return new RedactionStyle(bgColor, textColor, label, "", "box");
    }
}
