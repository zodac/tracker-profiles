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

package net.zodac.tracker.app;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.zodac.tracker.framework.config.ApplicationConfiguration;
import net.zodac.tracker.framework.config.Configuration;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Single source of truth for the output directory hierarchy used during a run: the root output directory and its {@code errors/} subdirectory.
 */
public final class ScreenshotDirectories {

    private static final Logger LOGGER = LogManager.getLogger();
    private static final ApplicationConfiguration CONFIG = Configuration.get();

    /**
     * The root output directory for the current run.
     */
    public static final Path OUTPUT = CONFIG.outputDirectory();

    /**
     * The directory where failure screenshots are written when a tracker fails.
     */
    public static final Path ERRORS = OUTPUT.resolve("errors");

    private ScreenshotDirectories() {

    }

    /**
     * Creates {@link #OUTPUT}. If {@link ApplicationConfiguration#takeScreenshotOnError()} is enabled, also creates {@link #ERRORS}. When the flag
     * is disabled the error directory is intentionally not created.
     *
     * @throws IOException if any directory cannot be created
     * @see Files#createDirectories(Path, java.nio.file.attribute.FileAttribute[])
     */
    public static void createAll() throws IOException {
        try {
            Files.createDirectories(OUTPUT);
            LOGGER.trace("Created screenshot output directory: [{}]", OUTPUT);

            if (CONFIG.takeScreenshotOnError()) {
                Files.createDirectories(ERRORS);
                LOGGER.trace("Created error screenshot directory: [{}]", ERRORS);
            }
        } catch (final IOException e) {
            throw new IOException("Unable to create output directories: %s".formatted(e.getMessage()), e);
        }
    }
}
