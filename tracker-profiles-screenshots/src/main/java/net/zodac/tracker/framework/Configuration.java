/*
 * BSD Zero Clause License
 *
 * Copyright (c) 2024-2025 zodac.net
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
 * Singleton class used to create an instance of {@link ApplicationConfiguration}.
 */
public final class Configuration {

    private Configuration() {

    }

    private static final class InstanceHolder {
        private static final ApplicationConfiguration INSTANCE = ApplicationConfiguration.load();
    }

    /**
     * Retrieve an instance of {@link ApplicationConfiguration}.
     *
     * @return the {@link ApplicationConfiguration}.
     */
    public static ApplicationConfiguration get() {
        return InstanceHolder.INSTANCE;
    }
}
