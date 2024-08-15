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
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import me.zodac.tracker.handler.ArHandler;
import me.zodac.tracker.handler.AthHandler;
import org.openqa.selenium.chrome.ChromeDriver;

/**
 * Utility class used to retrieve an instance of a {@link TrackerHandler}.
 */
public final class TrackerHandlerFactory {

    private static final List<Class<? extends TrackerHandler>> TRACKER_HANDLER_CLASSES = List.of(
        ArHandler.class,
        AthHandler.class
    );

    private TrackerHandlerFactory() {

    }

    /**
     * Finds an implementation of {@link TrackerHandler} that matches the wanted {@code trackerName}, and returns an instance of it. Implementations
     * of {@link TrackerHandler} should be annotated by {@link TrackerHandlerType}, which contains a {@link TrackerHandlerType#trackerCode()}, which
     * should match the input (the match is case-insensitive).
     *
     * @param trackerCode the abbreviated code of the tracker for which we want a {@link TrackerHandler}
     * @param driver      the {@link ChromeDriver} used to instantiate the {@link TrackerHandler}
     * @return an instance of the matching {@link TrackerHandler}
     * @throws IllegalArgumentException thrown if no valid {@link TrackerHandler} implementation could be found
     * @throws IllegalStateException    thrown if an error occured when instantiating the {@link TrackerHandler}
     */
    public static TrackerHandler getHandler(final String trackerCode, final ChromeDriver driver) {
        for (final Class<? extends TrackerHandler> trackerHandler : TRACKER_HANDLER_CLASSES) {
            if (hasMatchingAnnotation(trackerHandler, trackerCode)) {
                return makeNewInstance(trackerHandler, driver);
            }
        }

        throw new IllegalArgumentException(
            String.format("Unable to find %s with trackerName '%s'", TrackerHandler.class.getSimpleName(), trackerCode));
    }

    private static boolean hasMatchingAnnotation(final AnnotatedElement trackerHandler, final String trackerCode) {
        if (!trackerHandler.isAnnotationPresent(TrackerHandlerType.class)) {
            return false;
        }
        final TrackerHandlerType annotation = trackerHandler.getAnnotation(TrackerHandlerType.class);
        return annotation.trackerCode().equalsIgnoreCase(trackerCode);
    }

    private static TrackerHandler makeNewInstance(final Class<? extends TrackerHandler> attributeHandler, final ChromeDriver driver) {
        try {
            final Constructor<? extends TrackerHandler> constructorWithChromeDriver = attributeHandler.getDeclaredConstructor(ChromeDriver.class);
            return constructorWithChromeDriver.newInstance(driver);
        } catch (final IllegalAccessException | InstantiationException | InvocationTargetException | NoSuchMethodException e) {
            throw new IllegalStateException(String.format("Error instantiating an instance of '%s'", attributeHandler), e);
        }
    }
}
