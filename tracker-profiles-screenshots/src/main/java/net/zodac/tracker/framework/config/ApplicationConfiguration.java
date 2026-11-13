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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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
import java.util.Optional;
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
 * @param browserDataStoragePath         the file path in which to store browser data (profiles, caches, etc.)
 * @param browserDimensions              the dimensions in the format {@code width,height} for the {@code Selenium} web browser
 * @param csvCommentSymbol               the {@code char} defining a comment row in the CSV file
 * @param databasePath                   the {@link Path} to the SQLite database used by the web UI
 * @param enableAdultContent             whether to enable screenshots for trackers primarily hosting adult content
 * @param enableTranslationToEnglish     whether to translate non-English {@link TrackerType}s to English
 * @param existingScreenshotAction       the {@link ExistingScreenshotAction} to perform when a screenshot exists for a tracker
 * @param failOnUnsupportedTracker       whether to fail if an unsupported tracker is found in the CSV file
 * @param forceUiBrowser                 whether to use a UI-based browser or not
 * @param inputTimeoutDuration           how long to wait for a user-input (if enabled)
 * @param inputTimeoutEnabled            whether a timeout for a user-input is enabled or not
 * @param logLevel                       the log level for the application, must be one of: {@code INFO, DEBUG, TRACE, WARNING, ERROR}
 * @param logTrackerName                 whether to prefix each log message with the name of the tracker being screenshotted
 * @param numberOfParallelThreads        the number of parallel browser threads to use for {@link TrackerType#HEADLESS} trackers
 * @param numberOfScreenshotAttempts     the number of times to attempt to screenshot a tracker
 * @param outputDirectory                the output {@link Path} to the directory within which the screenshots will be saved
 * @param progressBarCompleteCharacter   the character used to represent a completed portion of the progress bar
 * @param progressBarEnabled             whether to display a progress bar at the bottom of the console output
 * @param progressBarFormat              the format string for the progress bar
 * @param progressBarIncompleteCharacter the character used to represent an incomplete portion of the progress bar
 * @param progressBarLength              the length (in characters) of the progress bar
 * @param redactionTypes                 the {@link RedactionType}s to perform redaction of sensitive information on the user profile page, in order
 * @param takeScreenshotOnError          whether to take a screenshot of the current page if an error occurs during screenshotting
 * @param trackerExecutionOrder          the execution order of the different {@link TrackerType}s
 * @param trackerInputFilePath           the {@link Path} to the input tracker CSV file
 * @param webUiEnabled                   whether to start the web UI rather than executing once from the CSV file
 * @param webUiPort                      the port on which the web UI is served
 */
public record ApplicationConfiguration(
    String browserDataStoragePath,
    String browserDimensions,
    char csvCommentSymbol,
    Path databasePath,
    boolean enableAdultContent,
    boolean enableTranslationToEnglish,
    ExistingScreenshotAction existingScreenshotAction,
    boolean failOnUnsupportedTracker,
    boolean forceUiBrowser,
    Duration inputTimeoutDuration,
    boolean inputTimeoutEnabled,
    String logLevel,
    boolean logTrackerName,
    int numberOfParallelThreads,
    int numberOfScreenshotAttempts,
    Path outputDirectory,
    char progressBarCompleteCharacter,
    boolean progressBarEnabled,
    String progressBarFormat,
    char progressBarIncompleteCharacter,
    int progressBarLength,
    Set<RedactionType> redactionTypes,
    boolean takeScreenshotOnError,
    Set<TrackerType> trackerExecutionOrder,
    Path trackerInputFilePath,
    boolean webUiEnabled,
    int webUiPort
) {

    private static final Logger LOGGER = LogManager.getLogger();

    private static final String BROWSER_DATA_STORAGE_PATH = "/tmp/chrome-home";

    private static final Set<String> VALID_LOG_LEVELS = Arrays.stream(Level.values())
        .map(Level::name)
        .collect(Collectors.toSet());

    private static final Set<String> VALID_RESOLUTIONS = new LinkedHashSet<>(List.of(
        "1280x1024",
        "1600x1000",
        "1600x1200",
        "1680x1050",
        "1920x1080",
        "1920x1200",
        "2560x1440",
        "7680x4320"
    ));

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
        final ApplicationConfiguration applicationConfiguration = new ApplicationConfiguration(
            BROWSER_DATA_STORAGE_PATH,
            getBrowserDimensions(source),
            getCsvCommentSymbol(source),
            Paths.get(source.get(ConfigurationOption.DATABASE_PATH)),
            getBooleanValue(source, ConfigurationOption.ENABLE_ADULT_TRACKERS),
            getBooleanValue(source, ConfigurationOption.ENABLE_TRANSLATION_TO_ENGLISH),
            getScreenshotExistsAction(source),
            getBooleanValue(source, ConfigurationOption.FAIL_ON_UNSUPPORTED_TRACKER),
            getBooleanValue(source, ConfigurationOption.FORCE_UI_BROWSER),
            getInputTimeoutDuration(source),
            getBooleanValue(source, ConfigurationOption.INPUT_TIMEOUT_ENABLED),
            getLogLevel(source),
            getBooleanValue(source, ConfigurationOption.LOG_TRACKER_NAME),
            getNumberOfParallelThreads(source),
            getNumberOfScreenshotAttempts(source),
            getOutputDirectory(source),
            parseSingleCharacter(source, ConfigurationOption.PROGRESS_BAR_COMPLETE_CHARACTER),
            getBooleanValue(source, ConfigurationOption.PROGRESS_BAR_ENABLED),
            getProgressBarFormat(source),
            parseSingleCharacter(source, ConfigurationOption.PROGRESS_BAR_INCOMPLETE_CHARACTER),
            getProgressBarLength(source),
            getRedactionTypes(source),
            getBooleanValue(source, ConfigurationOption.TAKE_SCREENSHOT_ON_ERROR),
            getTrackerExecutionOrder(source),
            getTrackerInputFilePath(source),
            getBooleanValue(source, ConfigurationOption.WEB_UI_ENABLED),
            getWebUiPort(source)
        );

        if (applicationConfiguration.progressBarCompleteCharacter() == applicationConfiguration.progressBarIncompleteCharacter()) {
            throw new IllegalArgumentException(
                "[PROGRESS_BAR_COMPLETE_CHARACTER][PROGRESS_BAR_INCOMPLETE_CHARACTER] Values must not be the same character");
        }

        applicationConfiguration.print();
        return applicationConfiguration;
    }

    private static String getBrowserDimensions(final Source source) {
        final String browserWidth = source.get(ConfigurationOption.BROWSER_WIDTH);
        final String browserHeight = source.get(ConfigurationOption.BROWSER_HEIGHT);
        final String resolution = "%sx%s".formatted(browserWidth, browserHeight);

        if (VALID_RESOLUTIONS.contains(resolution)) {
            return "%s,%s".formatted(browserWidth, browserHeight);
        }

        throw new IllegalArgumentException(
            "[BROWSER_WIDTH][BROWSER_HEIGHT] Unsupported browser resolution '%s', must be one of: %s".formatted(resolution, VALID_RESOLUTIONS));
    }

    private static char getCsvCommentSymbol(final Source source) {
        final String csvCommentSymbol = source.get(ConfigurationOption.CSV_COMMENT_SYMBOL);
        if (csvCommentSymbol.isEmpty()) {
            throw new IllegalArgumentException("[CSV_COMMENT_SYMBOL] Value must not be empty");
        }
        return csvCommentSymbol.charAt(0);
    }

    private static Duration getInputTimeoutDuration(final Source source) {
        final String inputTimeoutSeconds = source.get(ConfigurationOption.INPUT_TIMEOUT_SECONDS);
        final int inputTimeoutSecondsValidated = parseIntegerInRange(inputTimeoutSeconds, "INPUT_TIMEOUT_SECONDS", 1, Integer.MAX_VALUE);
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
            throw new IllegalArgumentException("[OUTPUT_DIRECTORY_NAME_FORMAT] Invalid time format: '%s'".formatted(timeZone), e);
        } catch (final DateTimeException e) {
            throw new IllegalArgumentException("[TIMEZONE] Invalid timezone: '%s'".formatted(timeZone), e);
        }
    }

    private static String getProgressBarFormat(final Source source) {
        final String format = source.get(ConfigurationOption.PROGRESS_BAR_FORMAT);
        if (format.isBlank()) {
            throw new IllegalArgumentException("[PROGRESS_BAR_FORMAT] Value must not be blank");
        }
        return format;
    }

    private static char parseSingleCharacter(final Source source, final ConfigurationOption option) {
        final String envVarName = option.name();
        final String raw = source.get(option);
        // Fast path: JVM decoded the env var correctly as a single Unicode code point
        if (raw.codePointCount(0, raw.length()) == 1) {
            return (char) raw.codePointAt(0);
        }

        // Overridden values do not come from the environment, so there are no raw bytes to recover
        if (source.isOverridden(option)) {
            throw new IllegalArgumentException("[%s] Must be a single character, got: '%s'".formatted(envVarName, raw));
        }

        // Slow path A: JVM decoded UTF-8 bytes using ISO-8859-1 (one char per byte)
        // ISO-8859-1 is byte-transparent (every byte value 0x00–0xFF maps to the same code point),
        // so getBytes(ISO_8859_1) recovers the original bytes, and we can re-decode as UTF-8
        final String fromLatin1 = new String(raw.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8);
        if (fromLatin1.codePointCount(0, fromLatin1.length()) == 1) {
            return (char) fromLatin1.codePointAt(0);
        }

        // Slow path B: JVM decoded using ASCII (or similar), replacing bytes > 0x7F with U+FFFD.
        // The original bytes are irretrievable from the Java string; read /proc/self/environ directly.
        final Optional<String> fromProcEnv = readRawEnvVarAsUtf8(envVarName);
        if (fromProcEnv.isPresent() && fromProcEnv.get().codePointCount(0, fromProcEnv.get().length()) == 1) {
            return (char) fromProcEnv.get().codePointAt(0);
        }

        throw new IllegalArgumentException("[%s] Must be a single character, got: '%s'".formatted(envVarName, raw));
    }

    private static Optional<String> readRawEnvVarAsUtf8(final String envVarName) {
        try {
            final byte[] environ = Files.readAllBytes(Paths.get("/proc/self/environ"));
            final byte[] searchPrefix = (envVarName + "=").getBytes(StandardCharsets.US_ASCII);
            int start = 0;
            for (int i = 0; i <= environ.length; i++) {
                if (i != environ.length && environ[i] != 0) {
                    continue;
                }
                if (entryStartsWith(environ, start, searchPrefix)) {
                    final int valueStart = start + searchPrefix.length;
                    return Optional.of(new String(environ, valueStart, i - valueStart, StandardCharsets.UTF_8));
                }
                start = i + 1;
            }
        } catch (final IOException e) {
            LOGGER.trace("Could not read /proc/self/environ to recover raw bytes for '{}': {}", envVarName, e.getMessage());
        }
        return Optional.empty();
    }

    private static boolean entryStartsWith(final byte[] bytes, final int offset, final byte[] prefix) {
        if (offset + prefix.length > bytes.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (bytes[offset + i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    private static int getProgressBarLength(final Source source) {
        final String raw = source.get(ConfigurationOption.PROGRESS_BAR_LENGTH);
        return parseIntegerInRange(raw, "PROGRESS_BAR_LENGTH", 10, 80);
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

    private static Path getTrackerInputFilePath(final Source source) {
        return Paths.get(source.get(ConfigurationOption.TRACKER_INPUT_FILE_PATH));
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

    private void print() {
        LOGGER.debug("Loaded application configuration:");
        LOGGER.debug("\t- browserDataStoragePath={}", browserDataStoragePath);
        LOGGER.debug("\t- browserDimensions={}", browserDimensions);
        LOGGER.debug("\t- csvCommentSymbol={}", csvCommentSymbol);
        LOGGER.debug("\t- databasePath={}", databasePath);
        LOGGER.debug("\t- enableAdultContent={}", enableAdultContent);
        LOGGER.debug("\t- enableTranslationToEnglish={}", enableTranslationToEnglish);
        LOGGER.debug("\t- existingScreenshotAction={}", existingScreenshotAction);
        LOGGER.debug("\t- failOnUnsupportedTracker={}", failOnUnsupportedTracker);
        LOGGER.debug("\t- forceUiBrowser={}", forceUiBrowser);
        LOGGER.debug("\t- inputTimeoutDuration={}", inputTimeoutDuration);
        LOGGER.debug("\t- inputTimeoutEnabled={}", inputTimeoutEnabled);
        LOGGER.debug("\t- logLevel={}", logLevel);
        LOGGER.debug("\t- logTrackerName={}", logTrackerName);
        LOGGER.debug("\t- numberOfParallelThreads={}", numberOfParallelThreads);
        LOGGER.debug("\t- numberOfScreenshotAttempts={}", numberOfScreenshotAttempts);
        LOGGER.debug("\t- outputDirectory={}", outputDirectory);
        LOGGER.debug("\t- progressBarCompleteCharacter={}", progressBarCompleteCharacter);
        LOGGER.debug("\t- progressBarEnabled={}", progressBarEnabled);
        LOGGER.debug("\t- progressBarFormat={}", progressBarFormat);
        LOGGER.debug("\t- progressBarIncompleteCharacter={}", progressBarIncompleteCharacter);
        LOGGER.debug("\t- progressBarLength={}", progressBarLength);
        LOGGER.debug("\t- redactionTypes={}", redactionTypes);
        LOGGER.debug("\t- takeScreenshotOnError={}", takeScreenshotOnError);
        LOGGER.debug("\t- trackerExecutionOrder={}", trackerExecutionOrder);
        LOGGER.debug("\t- trackerInputFilePath={}", trackerInputFilePath);
        LOGGER.debug("\t- webUiEnabled={}", webUiEnabled);
        LOGGER.debug("\t- webUiPort={}", webUiPort);
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

        boolean isOverridden(final ConfigurationOption option) {
            return overrides.containsKey(option.name());
        }
    }
}
