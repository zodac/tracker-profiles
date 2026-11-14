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

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unit tests for {@link ScreenshotPreviews}.
 */
class ScreenshotPreviewsTest {

    @TempDir
    private Path tempDirectory;

    @Test
    void givenTallScreenshot_whenThumbnailCreated_thenTopIsScaledToThumbnailWidth() throws IOException {
        // The top of the page is red, and the rest is blue
        final Path screenshot = writeScreenshot(tempDirectory.resolve("Tracker.png"), 1920, 8000, 1440);

        final BufferedImage thumbnail = read(ScreenshotPreviews.create().thumbnail(screenshot));

        assertThat(thumbnail.getWidth())
            .isEqualTo(ScreenshotPreviews.THUMBNAIL_WIDTH);
        assertThat(thumbnail.getHeight())
            .isEqualTo(ScreenshotPreviews.THUMBNAIL_HEIGHT);
        assertThat(new Color(thumbnail.getRGB(thumbnail.getWidth() / 2, thumbnail.getHeight() - 1)).getRed())
            .isGreaterThan(200);
    }

    @Test
    void givenSmallScreenshot_whenThumbnailCreated_thenItIsNotScaledUp() throws IOException {
        final Path screenshot = writeScreenshot(tempDirectory.resolve("Tracker.png"), 300, 200, 200);

        final BufferedImage thumbnail = read(ScreenshotPreviews.create().thumbnail(screenshot));

        assertThat(thumbnail.getWidth())
            .isEqualTo(300);
        assertThat(thumbnail.getHeight())
            .isEqualTo(200);
    }

    @Test
    void givenReplacedScreenshot_whenThumbnailRetrieved_thenNewThumbnailIsCreated() throws IOException {
        final ScreenshotPreviews screenshotPreviews = ScreenshotPreviews.create();
        final Path screenshot = writeScreenshot(tempDirectory.resolve("Tracker.png"), 1000, 1000, 1000);
        final byte[] original = screenshotPreviews.thumbnail(screenshot);

        assertThat(screenshotPreviews.thumbnail(screenshot))
            .isSameAs(original);

        writeScreenshot(screenshot, 600, 600, 600);
        Files.setLastModifiedTime(screenshot, FileTime.fromMillis(Files.getLastModifiedTime(screenshot).toMillis() + 1_000L));

        assertThat(read(screenshotPreviews.thumbnail(screenshot)).getWidth())
            .isEqualTo(ScreenshotPreviews.THUMBNAIL_WIDTH);
        assertThat(screenshotPreviews.thumbnail(screenshot))
            .isNotSameAs(original);
    }

    @Test
    void givenWideScreenshot_whenPreviewCreatedInStrips_thenWholeScreenshotIsScaledTo1080pWidthWithStripsInPlace() throws IOException {
        final Color[] bands = {Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW, Color.MAGENTA, Color.CYAN};
        final int bandHeight = 1_000;
        final BufferedImage image = new BufferedImage(3840, bandHeight * bands.length, BufferedImage.TYPE_INT_RGB);
        final Graphics2D graphics = image.createGraphics();
        try {
            for (int band = 0; band < bands.length; band++) {
                graphics.setColor(bands[band]);
                graphics.fillRect(0, band * bandHeight, image.getWidth(), bandHeight);
            }
        } finally {
            graphics.dispose();
        }
        final Path screenshot = tempDirectory.resolve("Wide.png");
        ImageIO.write(image, "PNG", screenshot.toFile());

        // A small budget, so the screenshot is decoded in strips of 300 rows (not aligned with the bands)
        final BufferedImage preview = read(ScreenshotPreviews.createPreview(screenshot, 3840L * 4L * 300L));

        assertThat(preview.getWidth())
            .isEqualTo(ScreenshotPreviews.PREVIEW_WIDTH);
        assertThat(preview.getHeight())
            .isEqualTo(bandHeight * bands.length / 2);
        for (int band = 0; band < bands.length; band++) {
            final Color actual = new Color(preview.getRGB(preview.getWidth() / 2, (band * bandHeight + bandHeight / 2) / 2));
            assertThat(colourDistance(actual, bands[band]))
                .as("Band %d", band)
                .isLessThan(30);
        }
    }

    @Test
    void givenScreenshotNoWiderThan1080p_whenPreviewRetrieved_thenNoPreviewIsCreated() throws IOException {
        final Path screenshot = writeScreenshot(tempDirectory.resolve("Tracker.png"), ScreenshotPreviews.PREVIEW_WIDTH, 4000, 1000);

        assertThat(ScreenshotPreviews.create().preview(screenshot))
            .isEmpty();
    }

    @Test
    void givenVeryTallScreenshot_whenPreviewRetrieved_thenItIsLimitedToMaximumHeight() throws IOException {
        final Path screenshot = tempDirectory.resolve("Tall.png");
        ImageIO.write(new BufferedImage(2400, 24_000, BufferedImage.TYPE_BYTE_GRAY), "PNG", screenshot.toFile());

        final BufferedImage preview = read(ScreenshotPreviews.create().preview(screenshot).orElseThrow());

        assertThat(preview.getHeight())
            .isEqualTo(ScreenshotPreviews.MAXIMUM_PREVIEW_HEIGHT);
        assertThat(preview.getWidth())
            .isEqualTo(1080);
    }

    private static int colourDistance(final Color first, final Color second) {
        return Math.abs(first.getRed() - second.getRed()) + Math.abs(first.getGreen() - second.getGreen())
            + Math.abs(first.getBlue() - second.getBlue());
    }

    private static Path writeScreenshot(final Path path, final int width, final int height, final int redHeight) throws IOException {
        final BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        final Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(Color.BLUE);
            graphics.fillRect(0, 0, width, height);
            graphics.setColor(Color.RED);
            graphics.fillRect(0, 0, width, redHeight);
        } finally {
            graphics.dispose();
        }
        ImageIO.write(image, "PNG", path.toFile());
        return path;
    }

    private static BufferedImage read(final byte[] thumbnail) throws IOException {
        return ImageIO.read(new ByteArrayInputStream(thumbnail));
    }
}
