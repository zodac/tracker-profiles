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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import me.zodac.tracker.handler.AbstractTrackerHandler;
import org.openqa.selenium.chrome.ChromeDriver;

/**
 * Utility class used to retrieve an instance of a {@link AbstractTrackerHandler}.
 */
public final class TrackerHandlerFactory {

    private static final Pattern PACKAGE_RENAMER_PATTERN = Pattern.compile("[.]");
    private static final Set<Class<?>> TRACKER_HANDLER_CLASSES = findAllClassesUsingClassLoader(AbstractTrackerHandler.class.getPackageName());

    private TrackerHandlerFactory() {

    }

    /**
     * Finds an implementation of {@link AbstractTrackerHandler} that matches the wanted {@code trackerName}, and returns an instance of it.
     * Implementations of {@link AbstractTrackerHandler} should be annotated by {@link TrackerHandlerType}, which contains a
     * {@link TrackerHandlerType#trackerCode()}, which should match the input (the match is case-insensitive).
     *
     * @param trackerCode the abbreviated code of the tracker for which we want a {@link AbstractTrackerHandler}
     * @param driver      the {@link ChromeDriver} used to instantiate the {@link AbstractTrackerHandler}
     * @return an instance of the matching {@link AbstractTrackerHandler}
     * @throws IllegalArgumentException thrown if no valid {@link AbstractTrackerHandler} implementation could be found
     * @throws IllegalStateException    thrown if an error occured when instantiating the {@link AbstractTrackerHandler}
     */
    public static AbstractTrackerHandler getHandler(final String trackerCode, final ChromeDriver driver) {
        for (final Class<?> trackerHandler : TRACKER_HANDLER_CLASSES) {
            if (hasMatchingAnnotation(trackerHandler, trackerCode)) {
                return makeNewInstance(trackerHandler, driver);
            }
        }

        throw new IllegalArgumentException(
            String.format("Unable to find %s with trackerName '%s'", AbstractTrackerHandler.class.getSimpleName(), trackerCode));
    }

    private static boolean hasMatchingAnnotation(final AnnotatedElement trackerHandler, final String trackerCode) {
        if (!trackerHandler.isAnnotationPresent(TrackerHandlerType.class)) {
            return false;
        }
        final TrackerHandlerType annotation = trackerHandler.getAnnotation(TrackerHandlerType.class);
        return annotation.trackerCode().equalsIgnoreCase(trackerCode);
    }

    private static AbstractTrackerHandler makeNewInstance(final Class<?> trackerHandler, final ChromeDriver driver) {
        try {
            // Reconfirming class type, but these should be already filtered
            if (AbstractTrackerHandler.class.isAssignableFrom(trackerHandler)) {
                throw new IllegalStateException(
                    String.format("%s is not an instance of %s", trackerHandler, AbstractTrackerHandler.class.getSimpleName()));
            }

            final Constructor<?> constructorWithChromeDriver = trackerHandler.getDeclaredConstructor(ChromeDriver.class);
            return (AbstractTrackerHandler) constructorWithChromeDriver.newInstance(driver);
        } catch (final IllegalAccessException | InstantiationException | InvocationTargetException | NoSuchMethodException e) {
            throw new IllegalStateException(String.format("Error instantiating an instance of '%s'", trackerHandler), e);
        }
    }

    private static Set<Class<?>> findAllClassesUsingClassLoader(final String packageName) {
        final String packageLoader = PACKAGE_RENAMER_PATTERN.matcher(packageName).replaceAll("/");
        try (
            final InputStream stream = ClassLoader.getSystemClassLoader().getResourceAsStream(packageLoader);
            final BufferedReader reader = new BufferedReader(new InputStreamReader(Objects.requireNonNull(stream), StandardCharsets.UTF_8))
        ) {
            return reader.lines()
                .filter(line -> line.endsWith(".class"))
                .map(line -> getClass(line, packageName))
                .filter(aClass -> aClass.isAnnotationPresent(TrackerHandlerType.class))
                .collect(Collectors.toSet());
        } catch (final IllegalStateException | IOException e) {
            return Set.of();
        }
    }

    private static Class<?> getClass(final String className, final String packageName) {
        try {
            return Class.forName(packageName + "." + className.substring(0, className.lastIndexOf('.')));
        } catch (final ClassNotFoundException ignored) {
            throw new IllegalStateException(String.format("Unable to retrieve class '%s' from package '%s'", className, packageName));
        }
    }
}
