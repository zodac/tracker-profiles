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

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Utility file that loads the application configuration from environment variables.
 *
 * @param browserDataStoragePath     the file path in which to store browser data (profiles, caches, etc.)
 * @param browserDimensions          the dimensions in the format {@code width,height} for the {@code Selenium} web browser
 * @param csvCommentSymbol           the {@code char} defining a comment row in the CSV file
 * @param forceUiBrowser             whether to use a UI-based browser or not
 * @param enableTranslationToEnglish whether to translate non-English {@link TrackerType}s to English
 * @param openOutputDirectory        whether to open the screenshot directory when execution is completed
 * @param outputDirectory            the output {@link Path} to the directory within which the screenshots will be saved
 * @param trackerExecutionOrder      the execution order of the different {@link TrackerType}s
 * @param trackerInputFilePath       the {@link Path} to the input tracker CSV file
 */
public record ApplicationConfiguration(
    String browserDataStoragePath,
    String browserDimensions,
    char csvCommentSymbol,
    boolean enableTranslationToEnglish,
    boolean forceUiBrowser,
    boolean openOutputDirectory,
    Path outputDirectory,
    List<TrackerType> trackerExecutionOrder,
    Path trackerInputFilePath
) {

    private static final Logger LOGGER = LogManager.getLogger();

    // Default values
    private static final String DEFAULT_BROWSER_DATA_STORAGE_PATH = File.separator + "tmp" + File.separator + "chrome";
    private static final String DEFAULT_BROWSER_WIDTH = "1680";
    private static final String DEFAULT_BROWSER_HEIGHT = "1050";
    private static final String DEFAULT_CSV_COMMENT_SYMBOL = "#";
    private static final String DEFAULT_OUTPUT_DIRECTORY_NAME_FORMAT = "yyyy-MM-dd";
    private static final String DEFAULT_OUTPUT_DIRECTORY_PARENT_PATH = File.separator + "tmp" + File.separator + "screenshots";
    private static final String DEFAULT_TIMEZONE = "UTC";
    private static final String DEFAULT_TRACKER_EXECUTION_ORDER = "headless,manual,non-english";
    private static final String DEFAULT_TRACKER_INPUT_FILE_PATH = DEFAULT_OUTPUT_DIRECTORY_PARENT_PATH + File.separator + "trackers.csv";

    /**
     * Loads the {@link ApplicationConfiguration} defined by environment variables.
     *
     * @return the {@link ApplicationConfiguration}
     */
    public static ApplicationConfiguration load() {
        final ApplicationConfiguration applicationConfiguration = new ApplicationConfiguration(
            getBrowserDataStoragePath(),
            getBrowserDimensions(),
            getCsvCommentSymbol(),
            getBooleanEnvironmentVariable("ENABLE_TRANSLATION_TO_ENGLISH", true),
            getBooleanEnvironmentVariable("FORCE_UI_BROWSER", false),
            getBooleanEnvironmentVariable("OPEN_OUTPUT_DIRECTORY", false),
            getOutputDirectory(),
            getTrackerExecutionOrder(),
            getTrackerInputFilePath()
        );

        applicationConfiguration.print();
        return applicationConfiguration;
    }

    private static String getBrowserDataStoragePath() {
        return getOrDefault("BROWSER_DATA_STORAGE_PATH", DEFAULT_BROWSER_DATA_STORAGE_PATH);
    }

    private static String getBrowserDimensions() {
        final String browserWidth = getOrDefault("BROWSER_WIDTH", DEFAULT_BROWSER_WIDTH);
        final String browserHeight = getOrDefault("BROWSER_HEIGHT", DEFAULT_BROWSER_HEIGHT);
        return String.format("%s,%s", browserWidth, browserHeight);
    }

    private static char getCsvCommentSymbol() {
        return getOrDefault("CSV_COMMENT_SYMBOL", DEFAULT_CSV_COMMENT_SYMBOL).charAt(0);
    }

    private static List<TrackerType> getTrackerExecutionOrder() {
        final String executionOrderRaw = getOrDefault("TRACKER_EXECUTION_ORDER", DEFAULT_TRACKER_EXECUTION_ORDER);
        final String[] executionOrderTokens = executionOrderRaw.split(",");
        if (executionOrderTokens.length == 0 || executionOrderTokens.length > TrackerType.ALL_VALUES.size()) {
            throw new IllegalArgumentException(
                String.format("Require 1-%d tracker types for EXECUTION_ORDER, found: %s", TrackerType.ALL_VALUES.size(),
                    Arrays.toString(executionOrderTokens)));
        }

        final Collection<TrackerType> trackerExecutionOrder = new LinkedHashSet<>();
        for (final String executionOrderToken : executionOrderTokens) {
            final TrackerType trackerType = TrackerType.find(executionOrderToken);
            if (trackerType == null) {
                throw new IllegalArgumentException(String.format("Invalid tracker found: '%s'", executionOrderToken));
            }

            if (!trackerExecutionOrder.add(trackerType)) {
                throw new IllegalArgumentException(String.format("Duplicate tracker found: '%s'", executionOrderToken));
            }
        }

        return List.copyOf(trackerExecutionOrder);
    }

    private static Path getOutputDirectory() {
        final String timeZone = getOrDefault("TIMEZONE", DEFAULT_TIMEZONE);
        final String outputDirectoryNameFormat = getOrDefault("OUTPUT_DIRECTORY_NAME_FORMAT", DEFAULT_OUTPUT_DIRECTORY_NAME_FORMAT);
        final String outputDirectoryParentPath = getOrDefault("OUTPUT_DIRECTORY_PARENT_PATH", DEFAULT_OUTPUT_DIRECTORY_PARENT_PATH);

        final LocalDate currentDate = LocalDate.now(ZoneId.of(timeZone));
        final String outputDirectoryName = currentDate.format(DateTimeFormatter.ofPattern(outputDirectoryNameFormat, Locale.getDefault()));
        return Paths.get(outputDirectoryParentPath, outputDirectoryName);
    }

    private static Path getTrackerInputFilePath() {
        return Paths.get(getOrDefault("TRACKER_INPUT_FILE_PATH", DEFAULT_TRACKER_INPUT_FILE_PATH));
    }

    private static boolean getBooleanEnvironmentVariable(final String environmentVariableName, final boolean defaultValue) {
        return Boolean.parseBoolean(getOrDefault(environmentVariableName, Boolean.toString(defaultValue)));
    }

    private static String getOrDefault(final String environmentVariableName, final String defaultValue) {
        final String environmentVariable = System.getenv(environmentVariableName);
        if (environmentVariable != null) {
            return environmentVariable;
        }
        return defaultValue;
    }

    private void print() {
        LOGGER.debug("Loaded application configuration:");
        LOGGER.debug("\t- browserDataStoragePath={}", browserDataStoragePath);
        LOGGER.debug("\t- browserDimensions={}", browserDimensions);
        LOGGER.debug("\t- csvCommentSymbol={}", csvCommentSymbol);
        LOGGER.debug("\t- enableTranslationToEnglish={}", enableTranslationToEnglish);
        LOGGER.debug("\t- forceUiBrowser={}", forceUiBrowser);
        LOGGER.debug("\t- openOutputDirectory={}", openOutputDirectory);
        LOGGER.debug("\t- outputDirectory={}", outputDirectory);
        LOGGER.debug("\t- trackerExecutionOrder={}", trackerExecutionOrder);
        LOGGER.debug("\t- trackerInputFilePath={}", trackerInputFilePath);
    }
}
