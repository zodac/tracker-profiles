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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.RecordComponent;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import net.zodac.tracker.framework.TrackerType;
import net.zodac.tracker.framework.redactor.RedactionType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Utility file that loads the application configuration from environment variables.
 *
 * @param browserDataStoragePath     the file path in which to store browser data (profiles, caches, etc.)
 * @param browserDimensions          the dimensions in the format {@code width,height} for the {@code Selenium} web browser
 * @param databasePath               the {@link Path} to the SQLite database used by the web UI
 * @param enableAdultContent         whether to enable screenshots for trackers primarily hosting adult content
 * @param enableTranslationToEnglish whether to translate non-English {@link TrackerType}s to English
 * @param existingScreenshotAction   the {@link ExistingScreenshotAction} to perform when a screenshot exists for a tracker
 * @param forceUiBrowser             whether to use a UI-based browser or not
 * @param inputTimeoutDuration       how long to wait for a user-input, where {@link Duration#ZERO} waits until the user responds
 * @param logLevel                   the log level for the application, must be one of: {@code INFO, DEBUG, TRACE, WARNING, ERROR}
 * @param numberOfParallelThreads    the number of parallel browser threads to use for {@link TrackerType#HEADLESS} trackers
 * @param numberOfScreenshotAttempts the number of times to attempt to screenshot a tracker
 * @param outputDirectory            the output {@link Path} to the directory within which the screenshots will be saved
 * @param redactionTypes             the {@link RedactionType}s to perform redaction of sensitive information on the user profile page, in order
 * @param secretKey                  the secret used to encrypt the passwords stored in the database
 * @param takeScreenshotOnError      whether to take a screenshot of the current page if an error occurs during screenshotting
 * @param timezone                   the local timezone, used for the name of the output directory and of downloaded screenshot archives
 * @param trackerExecutionOrder      the execution order of the different {@link TrackerType}s
 * @param webUiPort                  the port on which the web UI is served
 */
public record ApplicationConfiguration(
    String browserDataStoragePath,
    String browserDimensions,
    Path databasePath,
    boolean enableAdultContent,
    boolean enableTranslationToEnglish,
    ExistingScreenshotAction existingScreenshotAction,
    boolean forceUiBrowser,
    Duration inputTimeoutDuration,
    String logLevel,
    int numberOfParallelThreads,
    int numberOfScreenshotAttempts,
    Path outputDirectory,
    Set<RedactionType> redactionTypes,
    String secretKey,
    boolean takeScreenshotOnError,
    ZoneId timezone,
    Set<TrackerType> trackerExecutionOrder,
    int webUiPort
) {

    /**
     * The supported browser resolutions, in the format {@code widthxheight}.
     */
    public static final List<String> VALID_RESOLUTIONS = List.of(
        "1280x1024",
        "1600x1000",
        "1600x1200",
        "1680x1050",
        "1920x1080",
        "1920x1200",
        "2560x1440",
        "7680x4320"
    );

    private static final Logger LOGGER = LogManager.getLogger();

    private static final String BROWSER_DATA_STORAGE_PATH = "/tmp/chrome-home";
    private static final String SECRET_KEY_COMPONENT = "secretKey";
    private static final String MASKED_VALUE = "****";

    /**
     * Loads the {@link ApplicationConfiguration} defined by environment variables.
     *
     * @return the {@link ApplicationConfiguration}
     */
    public static ApplicationConfiguration load() {
        return load(Map.of());
    }

    /**
     * Loads the {@link ApplicationConfiguration}, with each value taken from (in order of precedence): the provided {@code overrides}, the
     * environment variable, then the {@link ConfigurationOption#defaultValue()}.
     *
     * <p>
     * Values are validated by their {@link ConfigurationOption#type()}: an {@link ConfigurationOption.OptionType#INTEGER} must be in its
     * {@link ConfigurationOption#range()}, and a {@link ConfigurationOption.OptionType#SELECT} or
     * {@link ConfigurationOption.OptionType#MULTI_SELECT} must be one of its {@link ConfigurationOption#allowedValues()}.
     *
     * @param overrides the values that override environment variables, keyed by {@link ConfigurationOption#name()}
     * @return the {@link ApplicationConfiguration}
     * @throws IllegalArgumentException thrown if any value is invalid
     */
    public static ApplicationConfiguration load(final Map<String, String> overrides) {
        final Source source = new Source(overrides);
        final ZoneId timezone = getTimezone(source);
        return new ApplicationConfiguration(
            BROWSER_DATA_STORAGE_PATH,
            source.select(ConfigurationOption.BROWSER_RESOLUTION).replace('x', ','),
            Paths.get(source.get(ConfigurationOption.DATABASE_PATH)),
            source.bool(ConfigurationOption.ENABLE_ADULT_TRACKERS),
            source.bool(ConfigurationOption.ENABLE_TRANSLATION_TO_ENGLISH),
            ExistingScreenshotAction.valueOf(source.select(ConfigurationOption.SCREENSHOT_EXISTS_ACTION)),
            source.bool(ConfigurationOption.FORCE_UI_BROWSER),
            Duration.ofSeconds(source.integer(ConfigurationOption.INPUT_TIMEOUT_SECONDS)),
            source.select(ConfigurationOption.LOG_LEVEL),
            source.integer(ConfigurationOption.NUMBER_OF_PARALLEL_THREADS),
            source.integer(ConfigurationOption.NUMBER_OF_SCREENSHOT_ATTEMPTS),
            getOutputDirectory(source, timezone),
            source.multiSelect(ConfigurationOption.REDACTION_TYPE, RedactionType::valueOf),
            source.get(ConfigurationOption.SECRET_KEY),
            source.bool(ConfigurationOption.TAKE_SCREENSHOT_ON_ERROR),
            timezone,
            source.multiSelect(ConfigurationOption.TRACKER_EXECUTION_ORDER, TrackerType::valueOf),
            source.integer(ConfigurationOption.WEB_UI_PORT)
        );
    }

    /**
     * Whether a user input times out, as defined by {@link #inputTimeoutDuration()}.
     *
     * @return {@code true} if a user input has a timeout
     */
    public boolean inputTimeoutEnabled() {
        return !inputTimeoutDuration.isZero();
    }

    private static Path getOutputDirectory(final Source source, final ZoneId timezone) {
        final String outputDirectoryNameFormat = source.get(ConfigurationOption.OUTPUT_DIRECTORY_NAME_FORMAT);
        final String outputDirectoryParentPath = source.get(ConfigurationOption.OUTPUT_DIRECTORY_PARENT_PATH);

        try {
            final LocalDate currentDate = LocalDate.now(timezone);
            final String outputDirectoryName = currentDate.format(DateTimeFormatter.ofPattern(outputDirectoryNameFormat, Locale.getDefault()));
            return Paths.get(outputDirectoryParentPath, outputDirectoryName);
        } catch (final IllegalArgumentException e) {
            throw new IllegalArgumentException("[OUTPUT_DIRECTORY_NAME_FORMAT] Invalid time format: '%s'".formatted(outputDirectoryNameFormat), e);
        }
    }

    private static ZoneId getTimezone(final Source source) {
        final String timezone = source.get(ConfigurationOption.TIMEZONE);
        try {
            return ZoneId.of(timezone);
        } catch (final DateTimeException e) {
            throw new IllegalArgumentException("[TIMEZONE] Invalid timezone: '%s'".formatted(timezone), e);
        }
    }

    /**
     * Logs every value of the {@link ApplicationConfiguration} at {@code DEBUG} level. Called when the application starts and when an execution
     * starts, rather than on each load, since settings are also loaded to validate each change made in the web UI.
     */
    public void print() {
        LOGGER.debug("Loaded application configuration:");
        for (final RecordComponent component : ApplicationConfiguration.class.getRecordComponents()) {
            LOGGER.debug("{}={}", component.getName(), printableValue(component));
        }
    }

    @Override
    public String toString() {
        return Arrays.stream(ApplicationConfiguration.class.getRecordComponents())
            .map(component -> "%s=%s".formatted(component.getName(), printableValue(component)))
            .collect(Collectors.joining(", ", "ApplicationConfiguration[", "]"));
    }

    // The value of a record component, with the secret key masked
    private Object printableValue(final RecordComponent component) {
        if (SECRET_KEY_COMPONENT.equals(component.getName())) {
            return secretKey.isEmpty() ? "" : MASKED_VALUE;
        }

        try {
            return component.getAccessor().invoke(this);
        } catch (final IllegalAccessException | InvocationTargetException e) {
            throw new IllegalStateException("Unable to read configuration value '%s'".formatted(component.getName()), e);
        }
    }

    // Resolves a value from the overrides, then the environment, then the default value, and parses it by its type
    private record Source(Map<String, String> overrides) {

        String get(final ConfigurationOption option) {
            final String override = overrides.get(option.name());
            if (override != null) {
                return override;
            }
            return option.environmentValue().orElse(option.defaultValue());
        }

        boolean bool(final ConfigurationOption option) {
            return Boolean.parseBoolean(get(option).trim());
        }

        int integer(final ConfigurationOption option) {
            return option.parseInteger(get(option));
        }

        // The allowed value matching the input, ignoring case
        String select(final ConfigurationOption option) {
            return option.parseAllowedValue(get(option));
        }

        <T> Set<T> multiSelect(final ConfigurationOption option, final Function<String, T> parser) {
            final Set<T> results = new LinkedHashSet<>();
            for (final String token : get(option).split(",", -1)) {
                final String value = option.parseAllowedValue(token);
                if (!results.add(parser.apply(value))) {
                    throw new IllegalArgumentException("[%s] Duplicate value found: '%s'".formatted(option.name(), value));
                }
            }
            return results;
        }
    }
}
