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

package net.zodac.tracker.web.storage;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Everything stored in the {@link ApplicationDatabase} except the tracker credentials (which are backed up as a CSV file, if at all), created by
 * {@link ApplicationDatabase#backup()} and restored by {@link ApplicationDatabase#restore(DatabaseBackup, List)}. Screenshots are only listed by
 * path: their files are backed up separately, if at all.
 *
 * @param settings    the settings saved in the web UI, keyed by environment variable name
 * @param results     the result of each tracker's most recent execution
 * @param screenshots the latest screenshots of each tracker
 * @param durations   the durations of each tracker's most recent successful executions
 */
public record DatabaseBackup(
    Map<String, String> settings,
    List<StoredTrackerResult> results,
    List<StoredTrackerScreenshot> screenshots,
    List<StoredTrackerDuration> durations
) {

    /**
     * Checks that the backup (read from a file uploaded by the user) is complete and consistent, so it can be restored. Values may be missing
     * (null), since the backup is read from JSON.
     *
     * @throws IllegalArgumentException thrown if any of its values is missing or duplicated
     */
    public void validate() {
        if (settings == null) {
            throw invalid("settings are missing");
        }
        if (settings.values().stream().anyMatch(Objects::isNull)) {
            throw invalid("a setting has no value");
        }
        requireUnique("settings", settings.keySet(), Function.identity());

        requireValid("results", results, result -> result.name() != null, StoredTrackerResult::name);
        requireValid("screenshots", screenshots, screenshot -> screenshot.name() != null && screenshot.id() != null && screenshot.path() != null,
            screenshot -> screenshot.name() + '/' + screenshot.id());
        requireValid("durations", durations, duration -> duration.name() != null && duration.duration() >= 0L,
            duration -> duration.name() + '/' + duration.finishedAt());
    }

    /**
     * Creates a copy of the backup with only some of its settings.
     *
     * @param restoredSettings the settings to keep, keyed by environment variable name
     * @return the {@link DatabaseBackup} with the settings
     */
    public DatabaseBackup withSettings(final Map<String, String> restoredSettings) {
        return new DatabaseBackup(Map.copyOf(restoredSettings), results, screenshots, durations);
    }

    /**
     * Creates a copy of the backup with other screenshots, such as with the paths of the screenshot files restored from the backup.
     *
     * @param restoredScreenshots the {@link StoredTrackerScreenshot}s
     * @return the {@link DatabaseBackup} with the screenshots
     */
    public DatabaseBackup withScreenshots(final List<StoredTrackerScreenshot> restoredScreenshots) {
        return new DatabaseBackup(settings, results, List.copyOf(restoredScreenshots), durations);
    }

    // Every item must be present (and complete), and have a different key (ignoring case, as tracker and setting names are stored)
    private static <T> void requireValid(final String name, final List<T> items, final Predicate<T> complete, final Function<T, String> key) {
        if (items == null) {
            throw invalid("%s are missing".formatted(name));
        }
        for (final T item : items) {
            if (item == null || !complete.test(item) || key.apply(item).isBlank()) {
                throw invalid("an entry of %s is incomplete".formatted(name));
            }
        }
        requireUnique(name, items, key);
    }

    private static <T> void requireUnique(final String name, final Collection<T> items, final Function<T, String> key) {
        final Set<String> keys = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (final T item : items) {
            if (!keys.add(key.apply(item))) {
                throw invalid("%s have more than one entry for '%s'".formatted(name, key.apply(item)));
            }
        }
    }

    private static IllegalArgumentException invalid(final String reason) {
        return new IllegalArgumentException("The backup is invalid: %s".formatted(reason));
    }
}
