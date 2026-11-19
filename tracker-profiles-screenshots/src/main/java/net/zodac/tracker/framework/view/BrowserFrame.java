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

package net.zodac.tracker.framework.view;

import java.io.IOException;
import java.io.OutputStream;

/**
 * A frame of a {@link BrowserView}: a JPEG image of the visible part of the page, with the size of the browser's viewport (in CSS pixels) used to
 * position the user's input.
 */
public final class BrowserFrame {

    private final long id;
    private final byte[] jpeg;
    private final double viewportWidth;
    private final double viewportHeight;

    private BrowserFrame(final long id, final byte[] jpeg, final double viewportWidth, final double viewportHeight) {
        this.id = id;
        this.jpeg = jpeg;
        this.viewportWidth = viewportWidth;
        this.viewportHeight = viewportHeight;
    }

    /**
     * Creates a {@link BrowserFrame}.
     *
     * @param id             the identifier of the frame, increasing with each frame of a {@link BrowserView}
     * @param jpeg           the JPEG image
     * @param viewportWidth  the width of the viewport, in CSS pixels
     * @param viewportHeight the height of the viewport, in CSS pixels
     * @return the {@link BrowserFrame}
     * @throws IllegalArgumentException thrown if the image is empty, or the viewport size is not positive
     */
    public static BrowserFrame of(final long id, final byte[] jpeg, final double viewportWidth, final double viewportHeight) {
        if (jpeg.length == 0) {
            throw new IllegalArgumentException("Frame image must not be empty");
        }
        if (viewportWidth <= 0 || viewportHeight <= 0) {
            throw new IllegalArgumentException("Viewport size must be positive, was %sx%s".formatted(viewportWidth, viewportHeight));
        }
        return new BrowserFrame(id, jpeg.clone(), viewportWidth, viewportHeight);
    }

    /**
     * The identifier of the frame, increasing with each frame of a {@link BrowserView}.
     *
     * @return the identifier
     */
    public long id() {
        return id;
    }

    /**
     * The size of the JPEG image, in bytes.
     *
     * @return the size
     */
    public int size() {
        return jpeg.length;
    }

    /**
     * Writes the JPEG image.
     *
     * @param outputStream the {@link OutputStream} to write to
     * @throws IOException thrown if the image cannot be written
     */
    public void writeTo(final OutputStream outputStream) throws IOException {
        outputStream.write(jpeg);
    }

    /**
     * The width of the browser's viewport, in CSS pixels.
     *
     * @return the width
     */
    public double viewportWidth() {
        return viewportWidth;
    }

    /**
     * The height of the browser's viewport, in CSS pixels.
     *
     * @return the height
     */
    public double viewportHeight() {
        return viewportHeight;
    }
}
