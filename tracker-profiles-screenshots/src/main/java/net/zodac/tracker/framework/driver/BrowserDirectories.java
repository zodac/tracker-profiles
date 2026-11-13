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
import net.zodac.tracker.framework.config.Configuration;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Single source of truth for the browser data directory hierarchy: the session profiles directory and the disk cache directory.
 */
final class BrowserDirectories {

    private static final Logger LOGGER = LogManager.getLogger();

    private BrowserDirectories() {

    }

    /**
     * The directory under which per-run Chrome user-data (session profile) directories are created.
     *
     * @return the sessions directory {@link Path}
     */
    static Path sessions() {
        return Path.of(Configuration.get().browserDataStoragePath(), "tracker-profiles-sessions");
    }

    /**
     * The directory used as Chrome's disk cache.
     *
     * @return the cache directory {@link Path}
     */
    static Path cache() {
        return Path.of(Configuration.get().browserDataStoragePath(), "selenium");
    }

    /**
     * Creates {@link #sessions()} and {@link #cache()} if they do not already exist.
     *
     * @throws IOException if either directory cannot be created
     * @see Files#createDirectories(Path, java.nio.file.attribute.FileAttribute[])
     */
    static void createAll() throws IOException {
        try {
            final Path sessions = sessions();
            Files.createDirectories(sessions);
            LOGGER.trace("Created browser sessions directory: [{}]", sessions);
            final Path cache = cache();
            Files.createDirectories(cache);
            LOGGER.trace("Created browser cache directory: [{}]", cache);
        } catch (final IOException e) {
            throw new IOException("Unable to create browser directories: %s".formatted(e.getMessage()), e);
        }
    }
}
