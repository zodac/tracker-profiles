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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.zodac.tracker.framework.config.ApplicationConfiguration;
import net.zodac.tracker.framework.config.Configuration;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Single source of truth for the browser data directory hierarchy: the session profiles directory and the disk cache directory.
 */
final class BrowserDirectories {

    private static final Logger LOGGER = LogManager.getLogger();
    private static final ApplicationConfiguration CONFIG = Configuration.get();

    /**
     * The directory under which per-run Chrome user-data (session profile) directories are created.
     */
    static final Path SESSIONS = Path.of(CONFIG.browserDataStoragePath(), "tracker-profiles-sessions");

    /**
     * The directory used as Chrome's disk cache.
     */
    static final Path CACHE = Path.of(CONFIG.browserDataStoragePath(), "selenium");

    private BrowserDirectories() {

    }

    /**
     * Creates {@link #SESSIONS} and {@link #CACHE} if they do not already exist.
     *
     * @throws IOException if either directory cannot be created
     * @see Files#createDirectories(Path, java.nio.file.attribute.FileAttribute[])
     */
    static void createAll() throws IOException {
        try {
            Files.createDirectories(SESSIONS);
            LOGGER.trace("Created browser sessions directory: [{}]", SESSIONS);
            Files.createDirectories(CACHE);
            LOGGER.trace("Created browser cache directory: [{}]", CACHE);
        } catch (final IOException e) {
            throw new IOException("Unable to create browser directories: %s".formatted(e.getMessage()), e);
        }
    }
}
