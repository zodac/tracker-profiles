/*
 * BSD Zero Clause License
 *
 * Copyright (c) 2021-2024 zodac.me
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

package me.zodac.tracker.framework;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import me.zodac.tracker.handler.AitherHandler;

public final class TrackerHandlerFactory {

    private static final List<Class<? extends TrackerHandler>> TRACKER_HANDLERS = List.of(
        AitherHandler.class
    );

    private TrackerHandlerFactory() {

    }

    public static TrackerHandler getHandler(final String trackerName) {
        for (final Class<? extends TrackerHandler> trackerHandler : TRACKER_HANDLERS) {
            if (hasMatchingAnnotation(trackerHandler, trackerName)) {
                return makeNewInstance(trackerHandler);
            }
        }

        throw new IllegalArgumentException(String.format("Unable to find %s with name '%s'", TrackerHandler.class.getSimpleName(), trackerName));
    }

    private static boolean hasMatchingAnnotation(final AnnotatedElement trackerHandler, final String trackerName) {
        if (!trackerHandler.isAnnotationPresent(TrackerType.class)) {
            return false;
        }
        final TrackerType annotation = trackerHandler.getAnnotation(TrackerType.class);
        return annotation.trackerName().equalsIgnoreCase(trackerName);
    }

    private static TrackerHandler makeNewInstance(final Class<? extends TrackerHandler> attributeHandler) {
        try {
            return attributeHandler.getConstructor().newInstance();
        } catch (final IllegalAccessException | InstantiationException | InvocationTargetException | NoSuchMethodException e) {
            throw new IllegalStateException(String.format("Error instantiating an instance of '%s'", attributeHandler), e);
        }
    }
}
