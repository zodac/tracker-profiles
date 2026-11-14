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

package net.zodac.tracker.framework.config;

import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import org.jspecify.annotations.Nullable;

/**
 * Singleton accessor for the application's {@link ApplicationConfiguration}.
 *
 * <p>
 * {@link #init()} must be called once (from {@code ApplicationLauncher}) before any call to {@link #get()}. Calling {@link #get()} before
 * {@link #init()} throws {@link IllegalStateException}.
 */
public final class Configuration {

    // Serialises updates, so a change made during an execution cannot be lost to a concurrent reload
    private static final Lock UPDATE_LOCK = new ReentrantLock();

    @Nullable
    private static volatile ApplicationConfiguration instance; // NOPMD: AvoidUsingVolatile - we want this to be flushed to memory ASAP

    private Configuration() {

    }

    /**
     * Loads and stores the {@link ApplicationConfiguration} from environment variables. Must be called exactly once at application startup, before
     * any call to {@link #get()}.
     *
     * @return the loaded {@link ApplicationConfiguration}
     * @throws IllegalArgumentException if any environment variable value is invalid
     */
    public static ApplicationConfiguration init() {
        final ApplicationConfiguration loaded = ApplicationConfiguration.load();
        loaded.print();
        instance = loaded;
        return loaded;
    }

    /**
     * Reloads and stores the {@link ApplicationConfiguration}, with the provided {@code overrides} taking precedence over environment variables.
     * Used by the web UI before each execution, so that settings changed in the UI take effect without restarting the application.
     *
     * <p>
     * Callers must never cache the result of {@link #get()} in a {@code static} field, otherwise a reload will not be visible to them.
     *
     * @param overrides the values that override environment variables, keyed by {@link ConfigurationOption#name()}
     * @return the loaded {@link ApplicationConfiguration}
     * @throws IllegalArgumentException if any value is invalid (the previously loaded configuration is retained)
     */
    public static ApplicationConfiguration reload(final Map<String, String> overrides) {
        UPDATE_LOCK.lock();
        try {
            final ApplicationConfiguration reloaded = ApplicationConfiguration.load(overrides);
            instance = reloaded;
            return reloaded;
        } finally {
            UPDATE_LOCK.unlock();
        }
    }

    /**
     * Applies the provided {@code overrides} to the {@link ConfigurationOption.Scope#LIVE} options only, keeping the current value of every other
     * option. Used by the web UI when a setting is changed during an execution, so that options read when the execution started (such as the
     * output directory) do not change part-way through it.
     *
     * @param overrides the values that override environment variables, keyed by {@link ConfigurationOption#name()}
     * @return the updated {@link ApplicationConfiguration}
     * @throws IllegalArgumentException if any value is invalid (the current configuration is retained)
     * @throws IllegalStateException    if {@link #init()} has not been called
     */
    public static ApplicationConfiguration applyLiveChanges(final Map<String, String> overrides) {
        UPDATE_LOCK.lock();
        try {
            final ApplicationConfiguration updated = get().withLiveValuesFrom(ApplicationConfiguration.load(overrides));
            instance = updated;
            return updated;
        } finally {
            UPDATE_LOCK.unlock();
        }
    }

    /**
     * Retrieve the {@link ApplicationConfiguration} loaded by {@link #init()}.
     *
     * @return the {@link ApplicationConfiguration}
     * @throws IllegalStateException if {@link #init()} has not been called
     */
    public static ApplicationConfiguration get() {
        final ApplicationConfiguration config = instance;
        if (config == null) {
            throw new IllegalStateException("Configuration.init() has not been called");
        }
        return config;
    }
}
