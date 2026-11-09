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

import java.lang.reflect.InvocationTargetException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import net.zodac.tracker.framework.annotation.TrackerHandler;
import net.zodac.tracker.framework.annotation.TrackerHandlers;
import net.zodac.tracker.handler.AbstractTrackerHandler;

/**
 * Factory class used to create an instance of a {@link AbstractTrackerHandler}.
 */
public final class TrackerHandlerFactory {

    private static final Map<String, Map.Entry<Class<?>, TrackerHandler>> TRACKER_HANDLES_BY_NAME = buildHandlerMap();

    private TrackerHandlerFactory() {

    }

    /**
     * Checks if an implementation of {@link AbstractTrackerHandler} exists that matches the wanted {@code trackerName}.
     *
     * @param trackerName the name of the tracker for which we want a {@link AbstractTrackerHandler}
     * @return {@link Optional} {@link TrackerHandler} for a matching {@code trackerName}
     */
    public static Optional<TrackerHandler> findMatchingHandler(final String trackerName) {
        return Optional.ofNullable(TRACKER_HANDLES_BY_NAME.get(trackerName.toLowerCase(Locale.ROOT)))
            .map(Map.Entry::getValue);
    }

    /**
     * Finds an implementation of {@link AbstractTrackerHandler} that matches the wanted {@code trackerName}, and returns an instance of it.
     * Implementations of {@link AbstractTrackerHandler} should be annotated by at least one {@link TrackerHandler}, which contains a
     * {@link TrackerHandler#name()}, which should match the input (the match is case-insensitive).
     *
     * @param trackerName the name of the tracker for which we want a {@link AbstractTrackerHandler}
     * @return an instance of the matching {@link AbstractTrackerHandler}
     * @throws IllegalStateException  thrown if an error occurred when instantiating the {@link AbstractTrackerHandler}
     * @throws NoSuchElementException thrown if no valid {@link AbstractTrackerHandler} implementation could be found
     */
    public static AbstractTrackerHandler getHandler(final String trackerName) {
        final var entry = TRACKER_HANDLES_BY_NAME.get(trackerName.toLowerCase(Locale.ROOT));
        if (entry == null) {
            throw new NoSuchElementException(
                "Unable to find %s with name '%s'".formatted(TrackerHandler.class.getSimpleName(), trackerName));
        }

        final TrackerDefinition trackerDefinition = TrackerDefinition.fromAnnotation(entry.getValue());
        return makeNewInstance(entry.getKey(), trackerDefinition);
    }

    private static Map<String, Map.Entry<Class<?>, TrackerHandler>> buildHandlerMap() {
        final Map<String, Map.Entry<Class<?>, TrackerHandler>> map = new HashMap<>();
        final var annotatedHandlers = PackageScanner.scan(
            AbstractTrackerHandler.class.getPackageName(),
            clazz -> AbstractTrackerHandler.class.isAssignableFrom(clazz)
            && (clazz.isAnnotationPresent(TrackerHandler.class) || clazz.isAnnotationPresent(TrackerHandlers.class))
        );
        for (final Class<?> clazz : annotatedHandlers) {
            for (final TrackerHandler annotation : clazz.getAnnotationsByType(TrackerHandler.class)) {
                map.put(annotation.name().toLowerCase(Locale.ROOT), Map.entry(clazz, annotation));
            }
        }
        return Collections.unmodifiableMap(map);
    }

    private static AbstractTrackerHandler makeNewInstance(final Class<?> trackerHandler, final TrackerDefinition trackerDefinition) {
        try {
            final AbstractTrackerHandler abstractTrackerHandler = (AbstractTrackerHandler) trackerHandler.getDeclaredConstructor().newInstance();
            abstractTrackerHandler.configure(trackerDefinition);
            return abstractTrackerHandler;
        } catch (final IllegalAccessException | InstantiationException | InvocationTargetException | NoSuchMethodException e) {
            throw new IllegalStateException("Error instantiating an instance of '%s'".formatted(trackerHandler), e);
        }
    }
}
