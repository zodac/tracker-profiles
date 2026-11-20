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

package net.zodac.tracker.framework.driver;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import net.zodac.tracker.framework.TrackerType;
import org.jspecify.annotations.Nullable;

/**
 * The resolution (width and height, in pixels) of a web browser. A headless browser always uses {@link #DEFAULT}, while a browser with a UI uses
 * the screen resolution of the web UI that started the execution ({@link #useForUiBrowsers(BrowserResolution)}), as it is shown there.
 */
public final class BrowserResolution {

    private static final int MINIMUM_WIDTH = 800;
    private static final int MINIMUM_HEIGHT = 600;
    private static final int MAXIMUM_WIDTH = 7680;
    private static final int MAXIMUM_HEIGHT = 4320;

    /**
     * The resolution of a headless browser, also used for a browser with a UI when the web UI did not send its screen resolution.
     */
    public static final BrowserResolution DEFAULT = of(1920, 1080);

    private static final AtomicReference<BrowserResolution> UI_BROWSER_RESOLUTION = new AtomicReference<>(DEFAULT);

    private final int width;
    private final int height;

    private BrowserResolution(final int width, final int height) {
        this.width = width;
        this.height = height;
    }

    /**
     * Creates a {@link BrowserResolution}.
     *
     * @param width  the width, in pixels
     * @param height the height, in pixels
     * @return the {@link BrowserResolution}
     * @throws IllegalArgumentException thrown if the width or height is outside the supported range
     */
    public static BrowserResolution of(final int width, final int height) {
        if (width < MINIMUM_WIDTH || width > MAXIMUM_WIDTH || height < MINIMUM_HEIGHT || height > MAXIMUM_HEIGHT) {
            throw new IllegalArgumentException("Invalid browser resolution %dx%d, must be between %dx%d and %dx%d"
                .formatted(width, height, MINIMUM_WIDTH, MINIMUM_HEIGHT, MAXIMUM_WIDTH, MAXIMUM_HEIGHT));
        }
        return new BrowserResolution(width, height);
    }

    /**
     * Creates a {@link BrowserResolution} from the screen resolution of the web UI, limited to the supported range. If either value is missing,
     * {@link #DEFAULT} is used.
     *
     * @param width  the width of the screen, in pixels
     * @param height the height of the screen, in pixels
     * @return the {@link BrowserResolution}
     */
    public static BrowserResolution fromScreen(final @Nullable Integer width, final @Nullable Integer height) {
        if (width == null || height == null) {
            return DEFAULT;
        }
        return of(Math.clamp(width, MINIMUM_WIDTH, MAXIMUM_WIDTH), Math.clamp(height, MINIMUM_HEIGHT, MAXIMUM_HEIGHT));
    }

    /**
     * Sets the {@link BrowserResolution} of every browser with a UI created from now on, called when an execution starts.
     *
     * @param resolution the {@link BrowserResolution}
     */
    public static void useForUiBrowsers(final BrowserResolution resolution) {
        UI_BROWSER_RESOLUTION.set(resolution);
    }

    /**
     * The {@link BrowserResolution} of a browser for a tracker of the given {@link TrackerType}.
     *
     * @param trackerType the {@link TrackerType} of the tracker
     * @return {@link #DEFAULT} for a headless browser, otherwise the resolution set by {@link #useForUiBrowsers(BrowserResolution)}
     */
    public static BrowserResolution forTrackerType(final TrackerType trackerType) {
        return JavaWebDriverFactory.usesHeadlessBrowser(trackerType) ? DEFAULT : Objects.requireNonNullElse(UI_BROWSER_RESOLUTION.get(), DEFAULT);
    }

    /**
     * The width, in pixels.
     *
     * @return the width
     */
    public int width() {
        return width;
    }

    /**
     * The height, in pixels.
     *
     * @return the height
     */
    public int height() {
        return height;
    }

    @Override
    public boolean equals(final Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof final BrowserResolution resolution)) {
            return false;
        }
        return width == resolution.width && height == resolution.height;
    }

    @Override
    public int hashCode() {
        return 31 * width + height;
    }

    @Override
    public String toString() {
        return "%dx%d".formatted(width, height);
    }
}
