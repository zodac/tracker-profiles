/*
 * BSD Zero Clause License
 *
 * Copyright (c) 2021-2025 zodac.net
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

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import net.zodac.tracker.framework.exception.DisabledTrackerException;
import net.zodac.tracker.handler.AbstractTrackerHandler;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

/**
 * Utility class used to retrieve an instance of a {@link AbstractTrackerHandler}.
 */
public final class TrackerHandlerFactory {

    private static final Pattern PACKAGE_SEPARATOR = Pattern.compile("[.]");
    private static final ConfigurationProperties CONFIG = Configuration.get();
    private static final Set<Class<?>> TRACKER_HANDLER_CLASSES = findAllClassesUsingClassLoader(AbstractTrackerHandler.class.getPackageName());

    private TrackerHandlerFactory() {

    }

    /**
     * Checks if an implementation of {@link AbstractTrackerHandler} exists that matches the wanted {@code trackerName}.
     *
     * @param trackerName the name of the tracker for which we want a {@link AbstractTrackerHandler}
     * @return {@link Optional} {@link TrackerHandler} for a matching {@code trackerName}
     */
    public static Optional<TrackerHandler> findMatchingHandler(final String trackerName) {
        return TRACKER_HANDLER_CLASSES.stream()
            .filter(trackerHandler -> trackerHandler.isAnnotationPresent(TrackerHandler.class))
            .map(trackerHandler -> trackerHandler.getAnnotation(TrackerHandler.class))
            .filter(annotation -> annotation.name().equalsIgnoreCase(trackerName))
            .findAny();
    }

    /**
     * Finds an implementation of {@link AbstractTrackerHandler} that matches the wanted {@code trackerName}, and returns an instance of it.
     * Implementations of {@link AbstractTrackerHandler} should be annotated by {@link TrackerHandler}, which contains a
     * {@link TrackerHandler#name()}, which should match the input (the match is case-insensitive).
     *
     * <p>
     * If the {@link AbstractTrackerHandler} also has the annotation {@link TrackerDisabled}, it will be skipped.
     *
     * <p>
     * A {@link ChromeDriver} will also be created and used to instantiate the {@link AbstractTrackerHandler}.
     *
     * @param trackerName the name of the tracker for which we want a {@link AbstractTrackerHandler}
     * @return an instance of the matching {@link AbstractTrackerHandler}
     * @throws DisabledTrackerException thrown is a {@link AbstractTrackerHandler} exists but is annotated by {@link TrackerDisabled}
     * @throws IllegalStateException    thrown if an error occurred when instantiating the {@link AbstractTrackerHandler}
     * @throws NoSuchElementException   thrown if no valid {@link AbstractTrackerHandler} implementation could be found
     */
    public static AbstractTrackerHandler getHandler(final String trackerName) {
        final var matchingTrackerHandlerOptional = TRACKER_HANDLER_CLASSES.stream()
            .filter(handler -> handler.isAnnotationPresent(TrackerHandler.class))
            .map(handler -> Map.entry(handler, handler.getAnnotation(TrackerHandler.class)))
            .filter(entry -> entry.getValue().name().equalsIgnoreCase(trackerName))
            .findFirst();

        if (matchingTrackerHandlerOptional.isEmpty()) {
            final String errorMessage = String.format("Unable to find %s with name '%s'", AbstractTrackerHandler.class.getSimpleName(), trackerName);
            throw new NoSuchElementException(errorMessage);
        }

        final var matchingTrackerHandler = matchingTrackerHandlerOptional.get();
        final Class<?> trackerHandler = matchingTrackerHandler.getKey();
        if (trackerHandler.isAnnotationPresent(TrackerDisabled.class)) {
            throw new DisabledTrackerException(trackerHandler.getAnnotation(TrackerDisabled.class).value());
        }

        final TrackerHandler annotation = matchingTrackerHandler.getValue();
        return makeNewInstance(trackerHandler, Arrays.asList(annotation.url()), annotation.needsManualInput());
    }

    private static AbstractTrackerHandler makeNewInstance(final Class<?> trackerHandler, final List<String> urls, final boolean isManualTracker) {
        try {
            final Constructor<?> constructorWithChromeDriverAndUrls = trackerHandler.getDeclaredConstructor(ChromeDriver.class, Collection.class);
            final ChromeDriver driver = createDriver(isManualTracker);
            return (AbstractTrackerHandler) constructorWithChromeDriverAndUrls.newInstance(driver, urls);
        } catch (final IllegalAccessException | InstantiationException | InvocationTargetException | NoSuchMethodException e) {
            throw new IllegalStateException(String.format("Error instantiating an instance of '%s'", trackerHandler), e);
        }
    }

    private static ChromeDriver createDriver(final boolean isManualTracker) {
        final ChromeOptions chromeOptions = new ChromeOptions();

        // User-defined options
        chromeOptions.addArguments("window-size=" + CONFIG.browserDimensions());
        if (!isManualTracker && CONFIG.useHeadlessBrowser()) {
            chromeOptions.addArguments("--headless=new");
            chromeOptions.addArguments("--start-maximized");
        }

        // Cache to avoid reloading data on subsequent runs
        chromeOptions.addArguments("--disk-cache-dir=" + CONFIG.browserDataStoragePath() + File.separator + "selenium");

        // Following 3 options are to ensure there are no conflicting issues running the browser on Linux
        chromeOptions.addArguments("--user-data-dir=" + CONFIG.browserDataStoragePath() + File.separator + System.nanoTime());
        chromeOptions.addArguments("--no-sandbox");
        chromeOptions.addArguments("--disable-dev-shm-usage");

        final Map<String, Object> driverPreferences = Map.of(
            // Disable password manager pop-ups
            "credentials_enable_service", false,
            "profile.password_manager_enabled", false
        );
        chromeOptions.setExperimentalOption("prefs", driverPreferences);

        // Additional flags to remove unnecessary information on browser
        chromeOptions.addArguments("--disable-gpu");
        chromeOptions.addArguments("--disable-notifications");
        chromeOptions.addArguments("disable-infobars");
        chromeOptions.setExperimentalOption("excludeSwitches", List.of("enable-automation"));

        return new ChromeDriver(chromeOptions);
    }

    private static Set<Class<?>> findAllClassesUsingClassLoader(final String packageName) {
        final String packageLoader = PACKAGE_SEPARATOR.matcher(packageName).replaceAll("/");
        try (final InputStream inputStream = Thread.currentThread().getContextClassLoader().getResourceAsStream(packageLoader);
             final BufferedReader reader = new BufferedReader(new InputStreamReader(Objects.requireNonNull(inputStream), StandardCharsets.UTF_8))
        ) {
            return reader.lines()
                .filter(line -> line.endsWith(".class"))
                .map(line -> getClass(line, packageName))
                .filter(aClass -> aClass.isAnnotationPresent(TrackerHandler.class))
                .collect(Collectors.toSet());
        } catch (final IOException _) {
            return Set.of();
        }
    }

    private static Class<?> getClass(final String className, final String packageName) {
        try {
            return Class.forName(packageName + "." + className.substring(0, className.lastIndexOf('.')));
        } catch (final ClassNotFoundException e) {
            throw new IllegalStateException(String.format("Unable to retrieve class '%s' from package '%s'", className, packageName), e);
        }
    }
}
