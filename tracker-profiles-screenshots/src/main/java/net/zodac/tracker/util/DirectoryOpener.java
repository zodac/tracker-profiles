/*
 * BSD Zero Clause License
 *
 * Copyright (c) 2021-2025 zodac.net
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

package net.zodac.tracker.util;

import java.io.File;
import java.io.IOException;
import java.util.Locale;

/**
 * Utility class used to open the screenshot directory at the end of execution.
 */
public final class DirectoryOpener {

    private DirectoryOpener() {

    }

    /**
     * Opens the provided directory using the appropriate OS-native command:
     * <ul>
     *     <li>Windows: {@code explorer}</li>
     *     <li>MacOS: {@code open}</li>
     *     <li>Linux: {@code xdg-open}</li>
     * </ul>
     *
     * @param directoryToOpen the directory to be opened
     * @throws IOException              thrown if there is any error opening the directory
     * @throws IllegalArgumentException thrown if the input {@link File} doesn't exist or is not a valid directory
     */
    public static void open(final File directoryToOpen) throws IOException {
        if (!directoryToOpen.exists() || !directoryToOpen.isDirectory()) {
            throw new IllegalArgumentException("Unable to find valid directory: " + directoryToOpen);
        }

        final String openDirectoryCommand = getCommand();
        new ProcessBuilder(openDirectoryCommand, directoryToOpen.getAbsolutePath())
            .redirectErrorStream(true) // Merges stderr into stdout
            .redirectOutput(ProcessBuilder.Redirect.DISCARD) // Discards output
            .start();
    }

    private static String getCommand() {
        final String operatingSystemName = System.getProperty("os.name").toLowerCase(Locale.getDefault());

        if (operatingSystemName.contains("win")) {
            return "explorer";
        } else if (operatingSystemName.contains("mac")) {
            return "open";
        } else {
            return "xdg-open";
        }
    }
}
