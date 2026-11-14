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

package net.zodac.tracker.framework;

/**
 * Provides the version of the application.
 */
public final class ApplicationVersion {

    private static final String UNKNOWN_VERSION = "unknown";

    private ApplicationVersion() {

    }

    /**
     * The version of the application, read from the {@code Implementation-Version} of the JAR manifest.
     *
     * @return the version, or {@code unknown} if the application is not run from its JAR (such as in tests)
     */
    public static String current() {
        final String version = ApplicationVersion.class.getPackage().getImplementationVersion();
        return version == null || version.isBlank() ? UNKNOWN_VERSION : version;
    }
}
