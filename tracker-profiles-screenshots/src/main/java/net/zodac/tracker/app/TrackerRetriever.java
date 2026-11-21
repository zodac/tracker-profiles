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
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.zodac.tracker.framework.ExecutionOrder;
import net.zodac.tracker.framework.TrackerCredential;
import net.zodac.tracker.framework.TrackerHandlerFactory;
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
     * Lists the provided {@link TrackerCredential}s in the order they are executed (by {@link ExecutionOrder}, then by name), excluding any tracker
     * that has no {@link TrackerHandler}.
     *
     * @param trackerCredentials the {@link TrackerCredential}s to execute
     * @param executionOrder     the {@link ExecutionOrder}
     * @return the {@link ScheduledTracker}s, in execution order
     */
    public static List<ScheduledTracker> schedule(final Collection<TrackerCredential> trackerCredentials, final ExecutionOrder executionOrder) {
        final Comparator<ScheduledTracker> order = Comparator
            .comparing(ScheduledTracker::type, executionOrder.typeOrder())
            .thenComparing(ScheduledTracker::credential);
        final List<ScheduledTracker> scheduledTrackers = trackerCredentials
            .stream()
            .flatMap(credential -> findHandler(credential).map(handler -> new ScheduledTracker(credential, handler.type())).stream())
            .sorted(order)
            .toList();

        LOGGER.trace("Trackers to be screenshot: {}", scheduledTrackers);
        return scheduledTrackers;
    }

    private static Optional<TrackerHandler> findHandler(final TrackerCredential trackerCredential) {
        final Optional<TrackerHandler> trackerHandlerOptional = TrackerHandlerFactory.findMatchingHandler(trackerCredential.name());
        if (trackerHandlerOptional.isEmpty()) {
            LOGGER.warn("No implementation found for tracker '{}'", trackerCredential.name());
            return Optional.empty();
        }

        return trackerHandlerOptional;
    }
}
