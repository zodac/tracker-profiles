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
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import net.zodac.tracker.framework.config.ApplicationConfiguration;
import net.zodac.tracker.framework.config.Configuration;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Single source of truth for the output directory hierarchy used during a run: the root output directory and its {@code errors/} subdirectory.
 */
public final class ScreenshotDirectories {

    private static final Logger LOGGER = LogManager.getLogger();
    // Parallel trackers may fail at the same time, so the error directory is only created by one of them
    private static final Lock ERRORS_DIRECTORY_LOCK = new ReentrantLock();

    private ScreenshotDirectories() {

    }

    /**
     * The root output directory for the current run. Resolved from the current {@link Configuration} on each call, since the configuration may
     * be reloaded between runs of the web UI.
     *
     * @return the output directory {@link Path}
     */
    public static Path output() {
        return Configuration.get().outputDirectory();
    }

    /**
     * The directory where failure screenshots are written when a tracker fails.
     *
     * @return the error directory {@link Path}
     */
    public static Path errors() {
        return output().resolve("errors");
    }

    /**
     * Creates {@link #output()}. If {@link ApplicationConfiguration#takeScreenshotOnError()} is enabled, also creates {@link #errors()}. When the
     * flag is disabled the error directory is not created, unless it is enabled during the execution (see {@link #createErrorsDirectory()}).
     *
     * @throws IOException if any directory cannot be created
     * @see Files#createDirectories(Path, java.nio.file.attribute.FileAttribute[])
     */
    public static void createAll() throws IOException {
        try {
            final Path output = output();
            Files.createDirectories(output);
            LOGGER.trace("Created screenshot output directory: [{}]", output);

            if (Configuration.get().takeScreenshotOnError()) {
                createErrorsDirectory();
            }
        } catch (final IOException e) {
            throw new IOException("Unable to create output directories: %s".formatted(e.getMessage()), e);
        }
    }

    /**
     * Creates {@link #errors()}, if it does not already exist. Called before each failure screenshot, since
     * {@link ApplicationConfiguration#takeScreenshotOnError()} can be enabled in the web UI during an execution.
     *
     * @throws IOException if the directory cannot be created
     */
    public static void createErrorsDirectory() throws IOException {
        ERRORS_DIRECTORY_LOCK.lock();
        try {
            final Path errors = errors();
            if (!Files.isDirectory(errors)) {
                Files.createDirectories(errors);
                LOGGER.trace("Created error screenshot directory: [{}]", errors);
            }
        } finally {
            ERRORS_DIRECTORY_LOCK.unlock();
        }
    }
}
