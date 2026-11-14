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

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import org.jspecify.annotations.Nullable;

/**
 * Creates the scaled-down images of screenshots shown in the web UI, as JPEG images.
 *
 * <p>
 * The images are:
 * <ul>
 *     <li>a thumbnail: the top of the (full-page) screenshot, at most {@link #THUMBNAIL_WIDTH} x {@link #THUMBNAIL_HEIGHT}</li>
 *     <li>a preview, shown in the gallery: the whole screenshot, scaled to {@link #PREVIEW_WIDTH} (1080p) wide, and at most
 *     {@link #MAXIMUM_PREVIEW_HEIGHT} tall</li>
 * </ul>
 *
 * <p>
 * A full-page screenshot at a high resolution can be too large to decode at once (a 7680x21600 screenshot needs over 600MB), so only every
 * {@code n}th pixel is decoded (keeping at least twice the target width, which is then scaled down smoothly), in horizontal strips of at most
 * {@link #STRIP_BUDGET_BYTES}. Only one image is created at a time, and images are cached (up to {@link #CACHE_BUDGET_BYTES}), keyed by the
 * screenshot file, its last modified time and the kind of image, so a replaced screenshot gets a new image.
 */
public final class ScreenshotPreviews {

    /**
     * The maximum width of a thumbnail in pixels (twice the width it is shown at, for high-density displays).
     */
    public static final int THUMBNAIL_WIDTH = 480;

    /**
     * The maximum height of a thumbnail in pixels.
     */
    public static final int THUMBNAIL_HEIGHT = 360;

    /**
     * The width of a preview in pixels. A screenshot that is not wider than this has no preview, since it can be shown as it is.
     */
    public static final int PREVIEW_WIDTH = 1920;

    /**
     * The maximum height of a preview in pixels (ten 1080p screens), limiting its memory. A taller screenshot is scaled down further.
     */
    public static final int MAXIMUM_PREVIEW_HEIGHT = 10_800;

    private static final int BYTES_PER_PIXEL = 4;
    private static final long STRIP_BUDGET_BYTES = 64L * 1024L * 1024L;
    private static final long CACHE_BUDGET_BYTES = 64L * 1024L * 1024L;
    private static final float THUMBNAIL_QUALITY = 0.85f;
    private static final float PREVIEW_QUALITY = 0.9f;

    private final Lock cacheLock = new ReentrantLock();
    // Decoding a screenshot needs a lot of memory, so only one image is created at a time
    private final Lock creationLock = new ReentrantLock();
    // In access order, so the least recently used image is removed first
    private final Map<CacheKey, byte[]> cache = new LinkedHashMap<>(16, 0.75f, true);
    private long cachedBytes;

    private ScreenshotPreviews() {

    }

    /**
     * Creates a {@link ScreenshotPreviews}.
     *
     * @return the {@link ScreenshotPreviews}
     */
    public static ScreenshotPreviews create() {
        return new ScreenshotPreviews();
    }

    /**
     * Retrieves the thumbnail of a screenshot, creating it if it has not been cached.
     *
     * @param screenshot the screenshot file
     * @return the thumbnail as JPEG bytes
     * @throws IOException thrown if the screenshot cannot be read, or the thumbnail cannot be created
     */
    public byte[] thumbnail(final Path screenshot) throws IOException {
        return cachedOrCreate(screenshot, ImageKind.THUMBNAIL);
    }

    /**
     * Retrieves the preview of a screenshot, creating it if it has not been cached.
     *
     * @param screenshot the screenshot file
     * @return the preview as JPEG bytes, or {@link Optional#empty()} if the screenshot is not wider than {@link #PREVIEW_WIDTH} (so it is shown
     *     as it is)
     * @throws IOException thrown if the screenshot cannot be read, or the preview cannot be created
     */
    public Optional<byte[]> preview(final Path screenshot) throws IOException {
        final byte[] preview = cachedOrCreate(screenshot, ImageKind.PREVIEW);
        return preview.length == 0 ? Optional.empty() : Optional.of(preview);
    }

    // An empty array is cached for a screenshot with no preview
    private byte[] cachedOrCreate(final Path screenshot, final ImageKind imageKind) throws IOException {
        final CacheKey cacheKey = new CacheKey(screenshot.toAbsolutePath(), Files.getLastModifiedTime(screenshot).toMillis(), imageKind);
        final byte[] cached = cached(cacheKey);
        if (cached != null) {
            return cached;
        }

        creationLock.lock();
        try {
            // Another request may have created it while waiting
            final byte[] createdMeanwhile = cached(cacheKey);
            if (createdMeanwhile != null) {
                return createdMeanwhile;
            }

            final byte[] image = imageKind == ImageKind.THUMBNAIL ? createThumbnail(screenshot) : createPreview(screenshot, STRIP_BUDGET_BYTES);
            store(cacheKey, image);
            return image;
        } finally {
            creationLock.unlock();
        }
    }

    private byte @Nullable [] cached(final CacheKey cacheKey) {
        cacheLock.lock();
        try {
            return cache.get(cacheKey);
        } finally {
            cacheLock.unlock();
        }
    }

    private void store(final CacheKey cacheKey, final byte[] image) {
        cacheLock.lock();
        try {
            // A replaced screenshot keeps the same path, so its old image is no longer needed
            final Iterator<Map.Entry<CacheKey, byte[]>> entries = cache.entrySet().iterator();
            while (entries.hasNext()) {
                final Map.Entry<CacheKey, byte[]> entry = entries.next();
                if (entry.getKey().path().equals(cacheKey.path()) && entry.getKey().imageKind() == cacheKey.imageKind()) {
                    cachedBytes -= entry.getValue().length;
                    entries.remove();
                }
            }

            cache.put(cacheKey, image);
            cachedBytes += image.length;
            final Iterator<byte[]> leastRecentlyUsedFirst = cache.values().iterator();
            while (cachedBytes > CACHE_BUDGET_BYTES && leastRecentlyUsedFirst.hasNext()) {
                cachedBytes -= leastRecentlyUsedFirst.next().length;
                leastRecentlyUsedFirst.remove();
            }
        } finally {
            cacheLock.unlock();
        }
    }

    /**
     * Creates the thumbnail of a screenshot: the top of the screenshot, scaled to at most {@link #THUMBNAIL_WIDTH} wide.
     *
     * @param screenshot the screenshot file
     * @return the thumbnail as JPEG bytes
     * @throws IOException thrown if the screenshot cannot be read, or the thumbnail cannot be created
     */
    static byte[] createThumbnail(final Path screenshot) throws IOException {
        try (final ScreenshotReader reader = ScreenshotReader.open(screenshot)) {
            final double scale = Math.min(1.0d, (double) THUMBNAIL_WIDTH / reader.width());
            final int shownHeight = Math.min(reader.height(), (int) Math.ceil(THUMBNAIL_HEIGHT / scale));
            final int width = Math.max(1, (int) Math.round(reader.width() * scale));
            final int height = Math.max(1, Math.min(THUMBNAIL_HEIGHT, (int) Math.round(shownHeight * scale)));
            return writeJpeg(reader.readScaled(shownHeight, width, height, STRIP_BUDGET_BYTES), THUMBNAIL_QUALITY);
        }
    }

    /**
     * Creates the preview of a screenshot: the whole screenshot, scaled to {@link #PREVIEW_WIDTH} wide (or narrower, if it would otherwise be
     * taller than {@link #MAXIMUM_PREVIEW_HEIGHT}).
     *
     * @param screenshot  the screenshot file
     * @param stripBudget the maximum size in bytes of each strip of the screenshot that is decoded
     * @return the preview as JPEG bytes, or an empty array if the screenshot is not wider than {@link #PREVIEW_WIDTH}
     * @throws IOException thrown if the screenshot cannot be read, or the preview cannot be created
     */
    static byte[] createPreview(final Path screenshot, final long stripBudget) throws IOException {
        try (final ScreenshotReader reader = ScreenshotReader.open(screenshot)) {
            if (reader.width() <= PREVIEW_WIDTH) {
                return new byte[0];
            }

            final double scale = Math.min((double) PREVIEW_WIDTH / reader.width(), (double) MAXIMUM_PREVIEW_HEIGHT / reader.height());
            final int width = Math.max(1, (int) Math.round(reader.width() * scale));
            final int height = Math.max(1, (int) Math.round(reader.height() * scale));
            return writeJpeg(reader.readScaled(reader.height(), width, height, stripBudget), PREVIEW_QUALITY);
        }
    }

    private static byte[] writeJpeg(final BufferedImage image, final float quality) throws IOException {
        final Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
        if (!writers.hasNext()) {
            throw new IOException("No JPEG image writer available");
        }

        final ImageWriter writer = writers.next();
        try (final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            final ImageOutputStream imageOutputStream = ImageIO.createImageOutputStream(outputStream)) {
            final ImageWriteParam writeParam = writer.getDefaultWriteParam();
            writeParam.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            writeParam.setCompressionQuality(quality);
            writer.setOutput(imageOutputStream);
            writer.write(null, new IIOImage(image, null, null), writeParam);
            imageOutputStream.flush();
            return outputStream.toByteArray();
        } finally {
            writer.dispose();
        }
    }

    // Halves the size until close to the target, then scales to it, which is smoother than scaling down in one step
    private static BufferedImage scaleDown(final BufferedImage source, final int width, final int height) {
        BufferedImage current = source;
        while (current.getWidth() / 2 >= width && current.getHeight() / 2 >= height) {
            current = draw(current, current.getWidth() / 2, current.getHeight() / 2);
        }
        return current.getWidth() == width && current.getHeight() == height ? current : draw(current, width, height);
    }

    // Drawn onto an opaque (white) image, since JPEG has no transparency
    private static BufferedImage draw(final BufferedImage source, final int width, final int height) {
        final BufferedImage target = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        final Graphics2D graphics = target.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, width, height);
            graphics.drawImage(source, 0, 0, width, height, null);
        } finally {
            graphics.dispose();
        }
        return target;
    }

    private enum ImageKind {
        THUMBNAIL,
        PREVIEW
    }

    private record CacheKey(Path path, long lastModified, ImageKind imageKind) {

    }

    // Reads a screenshot in strips, so the whole screenshot is never decoded at once
    private static final class ScreenshotReader implements AutoCloseable {

        private final ImageInputStream inputStream;
        private final ImageReader reader;

        private ScreenshotReader(final ImageInputStream inputStream, final ImageReader reader) {
            this.inputStream = inputStream;
            this.reader = reader;
        }

        static ScreenshotReader open(final Path screenshot) throws IOException {
            final ImageInputStream inputStream = ImageIO.createImageInputStream(screenshot.toFile());
            if (inputStream == null) {
                throw new IOException("Unable to open screenshot: %s".formatted(screenshot));
            }

            final Iterator<ImageReader> readers = ImageIO.getImageReaders(inputStream);
            if (!readers.hasNext()) {
                inputStream.close();
                throw new IOException("Unsupported image format: %s".formatted(screenshot));
            }

            final ImageReader reader = readers.next();
            // Not forward-only, since each strip is read from the start of the image data
            reader.setInput(inputStream, false, true);
            return new ScreenshotReader(inputStream, reader);
        }

        int width() throws IOException {
            return reader.getWidth(0);
        }

        int height() throws IOException {
            return reader.getHeight(0);
        }

        // Reads the top 'sourceHeight' rows of the screenshot, scaled to 'width' x 'height'. Every 'subsampling'th pixel is decoded, keeping at
        // least twice the target width, in strips of at most 'stripBudget' bytes
        BufferedImage readScaled(final int sourceHeight, final int width, final int height, final long stripBudget) throws IOException {
            final int sourceWidth = width();
            final int subsampling = Math.max(1, sourceWidth / (2 * width));
            final long decodedRowBytes = (long) Math.ceilDiv(sourceWidth, subsampling) * BYTES_PER_PIXEL;
            final int stripRows = (int) Math.max(subsampling, Math.min(sourceHeight, stripBudget / decodedRowBytes * subsampling));

            final BufferedImage target = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            final Graphics2D graphics = target.createGraphics();
            try {
                for (int stripStart = 0; stripStart < sourceHeight; stripStart += stripRows) {
                    final int stripEnd = Math.min(sourceHeight, stripStart + stripRows);
                    final int targetStart = (int) Math.round((double) stripStart * height / sourceHeight);
                    final int targetEnd = (int) Math.round((double) stripEnd * height / sourceHeight);
                    if (targetEnd <= targetStart) {
                        continue;
                    }

                    final ImageReadParam readParam = reader.getDefaultReadParam();
                    readParam.setSourceRegion(new Rectangle(0, stripStart, sourceWidth, stripEnd - stripStart));
                    readParam.setSourceSubsampling(subsampling, subsampling, 0, 0);
                    final BufferedImage strip = reader.read(0, readParam);
                    graphics.drawImage(scaleDown(strip, width, targetEnd - targetStart), 0, targetStart, null);
                }
            } finally {
                graphics.dispose();
            }
            return target;
        }

        @Override
        public void close() throws IOException {
            reader.dispose();
            inputStream.close();
        }
    }
}
