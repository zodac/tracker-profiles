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
import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import net.zodac.tracker.framework.TrackerCredential;
import net.zodac.tracker.framework.TrackerCsvReader;
import net.zodac.tracker.framework.TrackerHandlerFactory;
import net.zodac.tracker.framework.TrackerType;
import net.zodac.tracker.framework.annotation.TrackerHandler;
import net.zodac.tracker.framework.config.ApplicationConfiguration;
import net.zodac.tracker.framework.config.Configuration;
import net.zodac.tracker.framework.exception.InvalidCsvInputException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Handles retrieving and organising trackers from the CSV input file.
 */
public final class TrackerRetriever {

    private static final Logger LOGGER = LogManager.getLogger();

    private TrackerRetriever() {

    }

    /**
     * Reads trackers from the CSV file and organises them by {@link TrackerType}.
     *
     * @return {@link Map} of {@link TrackerType} to their credentials
     * @throws InvalidCsvInputException thrown if there is an error with the CSV input
     */
    public static Map<TrackerType, Set<TrackerCredential>> getTrackers() {
        LOGGER.trace("Retrieving trackers to execute");
        final Set<TrackerType> trackerExecutionOrder = Configuration.get().trackerExecutionOrder();
        LOGGER.debug("Tracker execution order: {}", trackerExecutionOrder);

        try {
            final Set<TrackerCredential> trackerCredentials = TrackerCsvReader.readTrackerCredentials();
            return groupByType(trackerCredentials);
        } catch (final InvalidCsvInputException e) {
            LOGGER.warn("Error with CSV input file content", e);
            return Map.of();
        } catch (final IOException e) {
            LOGGER.warn("Unable to read CSV input file", e);
            return Map.of();
        }
    }

    /**
     * Organises the provided {@link TrackerCredential}s by {@link TrackerType}, excluding any tracker that has no {@link TrackerHandler}, whose
     * {@link TrackerType} is not in {@link ApplicationConfiguration#trackerExecutionOrder()}, or is an adult tracker when
     * {@link ApplicationConfiguration#enableAdultContent()} is {@code false}.
     *
     * @param trackerCredentials the {@link TrackerCredential}s to organise
     * @return {@link Map} of {@link TrackerType} to their credentials
     */
    public static Map<TrackerType, Set<TrackerCredential>> groupByType(final Collection<TrackerCredential> trackerCredentials) {
        final Set<TrackerType> trackerExecutionOrder = Configuration.get().trackerExecutionOrder();
        final Map<TrackerType, Set<TrackerCredential>> trackersByType = new EnumMap<>(TrackerType.class);
        for (final TrackerCredential trackerCredential : trackerCredentials) {
            findHandler(trackerCredential, trackerExecutionOrder)
                .ifPresent(handler -> trackersByType.computeIfAbsent(handler.type(), _ -> new TreeSet<>()).add(trackerCredential));
        }

        LOGGER.trace("Trackers by type to be screenshot: {}", trackersByType);
        return trackersByType;
    }

    private static Optional<TrackerHandler> findHandler(final TrackerCredential trackerCredential, final Set<TrackerType> trackerExecutionOrder) {
        final Optional<TrackerHandler> trackerHandlerOptional = TrackerHandlerFactory.findMatchingHandler(trackerCredential.name());
        if (trackerHandlerOptional.isEmpty()) {
            LOGGER.warn("No implementation found for tracker '{}'", trackerCredential.name());
            return Optional.empty();
        }

        final TrackerHandler trackerHandler = trackerHandlerOptional.get();
        if (!trackerExecutionOrder.contains(trackerHandler.type())) {
            LOGGER.trace("Skipping {} ({})", trackerHandler.name(), trackerHandler.type());
            return Optional.empty();
        }

        if (!Configuration.get().enableAdultContent() && trackerHandler.adult()) {
            LOGGER.debug("Skipping adult tracker {}", trackerHandler.name());
            return Optional.empty();
        }

        return trackerHandlerOptional;
    }

    /**
     * Prints summary information about all {@link TrackerHandler}s, if {@link Logger#isDebugEnabled()} is {@code true}.
     *
     * @param trackersByType        the {@link Map} of trackers organised by {@link TrackerType}
     * @param trackerExecutionOrder the execution order of the {@link TrackerType}s
     */
    static void printTrackersInfo(final Map<TrackerType, Set<TrackerCredential>> trackersByType, final Set<TrackerType> trackerExecutionOrder) {
        if (LOGGER.isDebugEnabled()) {
            for (final TrackerType trackerType : trackerExecutionOrder) {
                trackerType.printSummary(trackersByType);
            }
        }
    }
}
