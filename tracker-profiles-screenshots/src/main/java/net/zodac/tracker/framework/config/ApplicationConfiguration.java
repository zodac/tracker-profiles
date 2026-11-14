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
import org.apache.logging.log4j.Level;
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
 * @param takeScreenshotOnError      whether to take a screenshot of the current page if an error occurs during screenshotting
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
    boolean takeScreenshotOnError,
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

    private static final Set<String> VALID_LOG_LEVELS = Arrays.stream(Level.values())
        .map(Level::name)
        .collect(Collectors.toSet());

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
     * @param overrides the values that override environment variables, keyed by {@link ConfigurationOption#name()}
     * @return the {@link ApplicationConfiguration}
     * @throws IllegalArgumentException thrown if any value is invalid
     */
    public static ApplicationConfiguration load(final Map<String, String> overrides) {
        final Source source = new Source(overrides);
        return new ApplicationConfiguration(
            BROWSER_DATA_STORAGE_PATH,
            getBrowserDimensions(source),
            Paths.get(source.get(ConfigurationOption.DATABASE_PATH)),
            getBooleanValue(source, ConfigurationOption.ENABLE_ADULT_TRACKERS),
            getBooleanValue(source, ConfigurationOption.ENABLE_TRANSLATION_TO_ENGLISH),
            getScreenshotExistsAction(source),
            getBooleanValue(source, ConfigurationOption.FORCE_UI_BROWSER),
            getInputTimeoutDuration(source),
            getLogLevel(source),
            getNumberOfParallelThreads(source),
            getNumberOfScreenshotAttempts(source),
            getOutputDirectory(source),
            getRedactionTypes(source),
            getBooleanValue(source, ConfigurationOption.TAKE_SCREENSHOT_ON_ERROR),
            getTrackerExecutionOrder(source),
            getWebUiPort(source)
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

    /**
     * Creates a copy of this {@link ApplicationConfiguration}, with the value of every {@link ConfigurationOption.Scope#LIVE} option taken from
     * {@code liveValues}. Used to apply changes made during an execution, without changing the options that are only read when an execution
     * starts.
     *
     * @param liveValues the {@link ApplicationConfiguration} holding the new values of the live options
     * @return the updated {@link ApplicationConfiguration}
     */
    public ApplicationConfiguration withLiveValuesFrom(final ApplicationConfiguration liveValues) {
        return new ApplicationConfiguration(
            browserDataStoragePath,
            browserDimensions,
            databasePath,
            enableAdultContent,
            enableTranslationToEnglish,
            existingScreenshotAction,
            forceUiBrowser,
            liveValues.inputTimeoutDuration(),
            liveValues.logLevel(),
            numberOfParallelThreads,
            liveValues.numberOfScreenshotAttempts(),
            outputDirectory,
            redactionTypes,
            liveValues.takeScreenshotOnError(),
            liveValues.trackerExecutionOrder(),
            webUiPort
        );
    }

    private static String getBrowserDimensions(final Source source) {
        final String resolution = source.get(ConfigurationOption.BROWSER_RESOLUTION).trim().toLowerCase(Locale.ROOT);
        if (VALID_RESOLUTIONS.contains(resolution)) {
            return resolution.replace('x', ',');
        }

        throw new IllegalArgumentException(
            "[BROWSER_RESOLUTION] Unsupported browser resolution '%s', must be one of: %s".formatted(resolution, VALID_RESOLUTIONS));
    }

    private static Duration getInputTimeoutDuration(final Source source) {
        final String inputTimeoutSeconds = source.get(ConfigurationOption.INPUT_TIMEOUT_SECONDS);
        final int inputTimeoutSecondsValidated = parseIntegerInRange(inputTimeoutSeconds, "INPUT_TIMEOUT_SECONDS", 0, Integer.MAX_VALUE);
        return Duration.ofSeconds(inputTimeoutSecondsValidated);
    }

    private static String getLogLevel(final Source source) {
        final String logLevelRaw = source.get(ConfigurationOption.LOG_LEVEL).toUpperCase(Locale.ROOT);
        if (!VALID_LOG_LEVELS.contains(logLevelRaw)) {
            throw new IllegalArgumentException(
                "[LOG_LEVEL] Invalid value: '%s', must be one of: %s".formatted(logLevelRaw, VALID_LOG_LEVELS));
        }
        return logLevelRaw;
    }

    private static int getNumberOfParallelThreads(final Source source) {
        final String raw = source.get(ConfigurationOption.NUMBER_OF_PARALLEL_THREADS);
        return parseIntegerInRange(raw, "NUMBER_OF_PARALLEL_THREADS", 1, 32);
    }

    private static int getNumberOfScreenshotAttempts(final Source source) {
        final String numberOfRetriesRaw = source.get(ConfigurationOption.NUMBER_OF_SCREENSHOT_ATTEMPTS);
        return parseIntegerInRange(numberOfRetriesRaw, "NUMBER_OF_SCREENSHOT_ATTEMPTS", 1, 5);
    }

    private static Path getOutputDirectory(final Source source) {
        final String timeZone = source.get(ConfigurationOption.TIMEZONE);
        final String outputDirectoryNameFormat = source.get(ConfigurationOption.OUTPUT_DIRECTORY_NAME_FORMAT);
        final String outputDirectoryParentPath = source.get(ConfigurationOption.OUTPUT_DIRECTORY_PARENT_PATH);

        try {
            final LocalDate currentDate = LocalDate.now(ZoneId.of(timeZone));
            final String outputDirectoryName = currentDate.format(DateTimeFormatter.ofPattern(outputDirectoryNameFormat, Locale.getDefault()));
            return Paths.get(outputDirectoryParentPath, outputDirectoryName);
        } catch (final IllegalArgumentException e) {
            throw new IllegalArgumentException("[OUTPUT_DIRECTORY_NAME_FORMAT] Invalid time format: '%s'".formatted(outputDirectoryNameFormat), e);
        } catch (final DateTimeException e) {
            throw new IllegalArgumentException("[TIMEZONE] Invalid timezone: '%s'".formatted(timeZone), e);
        }
    }

    private static Set<RedactionType> getRedactionTypes(final Source source) {
        return parseCommaSeparatedValue(source, ConfigurationOption.REDACTION_TYPE, RedactionType::find);
    }

    private static ExistingScreenshotAction getScreenshotExistsAction(final Source source) {
        final String existingScreenshotActionRaw = source.get(ConfigurationOption.SCREENSHOT_EXISTS_ACTION);
        final ExistingScreenshotAction existingScreenshotActionInput = ExistingScreenshotAction.get(existingScreenshotActionRaw);
        if (existingScreenshotActionInput == null) {
            throw new IllegalArgumentException("[SCREENSHOT_EXISTS_ACTION] Invalid value: '%s'".formatted(existingScreenshotActionRaw));
        }

        return existingScreenshotActionInput;
    }

    private static Set<TrackerType> getTrackerExecutionOrder(final Source source) {
        return parseCommaSeparatedValue(source, ConfigurationOption.TRACKER_EXECUTION_ORDER, TrackerType::find);
    }

    private static <T> Set<T> parseCommaSeparatedValue(final Source source, final ConfigurationOption option, final Function<String, T> parser) {
        final String envVarName = option.name();
        final String rawValue = source.get(option);
        final Set<T> results = new LinkedHashSet<>();

        for (final String token : rawValue.split(",", -1)) {
            final String trimmed = token.trim();
            final T value = parser.apply(trimmed);

            if (value == null) {
                throw new IllegalArgumentException("[%s] Invalid value: '%s'".formatted(envVarName, trimmed));
            }

            if (!results.add(value)) {
                throw new IllegalArgumentException("[%s] Duplicate value found: '%s'".formatted(envVarName, trimmed));
            }
        }
        return results;
    }

    private static int getWebUiPort(final Source source) {
        return parseIntegerInRange(source.get(ConfigurationOption.WEB_UI_PORT), ConfigurationOption.WEB_UI_PORT.name(), 1, 65_535);
    }

    private static boolean getBooleanValue(final Source source, final ConfigurationOption option) {
        return Boolean.parseBoolean(source.get(option));
    }

    private static int parseIntegerInRange(final String input, final String environmentVariableName, final int min, final int max) {
        try {
            final int integer = Integer.parseInt(input);
            if (integer < min || integer > max) {
                throw new IllegalArgumentException(
                    "[%s] Invalid input '%s', must be between %d and %d".formatted(environmentVariableName, input, min, max));
            }
            return integer;
        } catch (final NumberFormatException e) {
            throw new IllegalArgumentException("[%s] Invalid input '%s', must be a valid number".formatted(environmentVariableName, input), e);
        }
    }

    /**
     * Logs every value of the {@link ApplicationConfiguration} at {@code DEBUG} level. Called when the application starts and when an execution
     * starts, rather than on each load, since settings are also loaded to validate each change made in the web UI.
     */
    public void print() {
        LOGGER.debug("Loaded application configuration:");
        LOGGER.debug("browserDataStoragePath={}", browserDataStoragePath);
        LOGGER.debug("browserDimensions={}", browserDimensions);
        LOGGER.debug("databasePath={}", databasePath);
        LOGGER.debug("enableAdultContent={}", enableAdultContent);
        LOGGER.debug("enableTranslationToEnglish={}", enableTranslationToEnglish);
        LOGGER.debug("existingScreenshotAction={}", existingScreenshotAction);
        LOGGER.debug("forceUiBrowser={}", forceUiBrowser);
        LOGGER.debug("inputTimeoutDuration={}", inputTimeoutDuration);
        LOGGER.debug("logLevel={}", logLevel);
        LOGGER.debug("numberOfParallelThreads={}", numberOfParallelThreads);
        LOGGER.debug("numberOfScreenshotAttempts={}", numberOfScreenshotAttempts);
        LOGGER.debug("outputDirectory={}", outputDirectory);
        LOGGER.debug("redactionTypes={}", redactionTypes);
        LOGGER.debug("takeScreenshotOnError={}", takeScreenshotOnError);
        LOGGER.debug("trackerExecutionOrder={}", trackerExecutionOrder);
        LOGGER.debug("webUiPort={}", webUiPort);
    }

    // Resolves a value from the overrides, then the environment, then the default value
    private record Source(Map<String, String> overrides) {

        String get(final ConfigurationOption option) {
            final String override = overrides.get(option.name());
            if (override != null) {
                return override;
            }
            return option.environmentValue().orElse(option.defaultValue());
        }
    }
}
