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

import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import net.zodac.tracker.framework.TrackerCredential;
import net.zodac.tracker.framework.TrackerHandlerFactory;
import net.zodac.tracker.framework.TrackerType;
import net.zodac.tracker.framework.annotation.TrackerHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Handles organising the trackers to be executed.
 */
public final class TrackerRetriever {

    private static final Logger LOGGER = LogManager.getLogger();

    private TrackerRetriever() {

    }

    /**
     * Organises the provided {@link TrackerCredential}s by {@link TrackerType}, excluding any tracker that has no {@link TrackerHandler}.
     *
     * @param trackerCredentials the {@link TrackerCredential}s to organise
     * @return {@link Map} of {@link TrackerType} to their credentials
     */
    public static Map<TrackerType, Set<TrackerCredential>> groupByType(final Collection<TrackerCredential> trackerCredentials) {
        final Map<TrackerType, Set<TrackerCredential>> trackersByType = new EnumMap<>(TrackerType.class);
        for (final TrackerCredential trackerCredential : trackerCredentials) {
            findHandler(trackerCredential)
                .ifPresent(handler -> trackersByType.computeIfAbsent(handler.type(), _ -> new TreeSet<>()).add(trackerCredential));
        }

        LOGGER.trace("Trackers by type to be screenshot: {}", trackersByType);
        return trackersByType;
    }

    private static Optional<TrackerHandler> findHandler(final TrackerCredential trackerCredential) {
        final Optional<TrackerHandler> trackerHandlerOptional = TrackerHandlerFactory.findMatchingHandler(trackerCredential.name());
        if (trackerHandlerOptional.isEmpty()) {
            LOGGER.warn("No implementation found for tracker '{}'", trackerCredential.name());
            return Optional.empty();
        }

        return trackerHandlerOptional;
    }

    /**
     * Prints summary information about all {@link TrackerHandler}s, if {@link Logger#isDebugEnabled()} is {@code true}.
     *
     * @param trackersByType the {@link Map} of trackers organised by {@link TrackerType}
     */
    static void printTrackersInfo(final Map<TrackerType, Set<TrackerCredential>> trackersByType) {
        if (LOGGER.isDebugEnabled()) {
            for (final TrackerType trackerType : TrackerType.values()) {
                trackerType.printSummary(trackersByType);
            }
        }
    }
}
