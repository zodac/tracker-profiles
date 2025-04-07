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
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Utility file that loads the application configuration from environment variables.
 *
 * @param browserDataStoragePath the file path in which to store browser data (profiles, caches, etc.)
 * @param browserDimensions      the dimensions in the format {@code width,height} for the {@code Selenium} web browser
 * @param csvCommentSymbol       the {@code char} defining a comment row in the CSV file
 * @param emailAddresses         a {@link Collection} of email addresses to be redacted from screenshots
 * @param includeManualTrackers  whether to include trackers that require a manual user interaction
 * @param ipAddresses            a {@link Collection} of IP addresses to be redacted from screenshots (including the first half of each address)
 * @param openOutputDirectory    whether to open the screenshot directory when execution is completed
 * @param outputDirectory        the output {@link Path} to the directory within which the screenshots will be saved
 * @param trackerInputFilePath   the {@link Path} to the input tracker CSV file
 * @param useHeadlessBrowser     whether to use a headless browser or not
 */
// TODO: Possible to remove need for email/IP addresses, and just redact the full element by XPath? Could do regex to IP/email, or just paint it?
public record ApplicationConfiguration(
    String browserDataStoragePath,
    String browserDimensions,
    char csvCommentSymbol,
    Collection<String> emailAddresses,
    boolean includeManualTrackers,
    Collection<String> ipAddresses,
    boolean openOutputDirectory,
    Path outputDirectory,
    Path trackerInputFilePath,
    boolean useHeadlessBrowser
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
            getEmailAddresses(),
            getBooleanEnvironmentVariable("INCLUDE_MANUAL_TRACKERS"),
            getIpAddresses(),
            getBooleanEnvironmentVariable("OPEN_OUTPUT_DIRECTORY"),
            getOutputDirectory(),
            getInputFilePath(),
            getBooleanEnvironmentVariable("USE_HEADLESS_BROWSER")
        );
        LOGGER.debug("Loaded properties: {}", applicationConfiguration);
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

    private static Collection<String> getEmailAddresses() {
        final String value = getOrDefault("EMAIL_ADDRESSES", "");
        return List.of(value.split(","));
    }

    private static Collection<String> getIpAddresses() {
        final String value = getOrDefault("IP_ADDRESSES", "");
        final Collection<String> rawIpAddresses = List.of(value.split(","));

        // Need to include half IP addresses for some tracker activity logs (like HDBits)
        final Collection<String> halfIpAddresses = rawIpAddresses
            .stream()
            .map(ApplicationConfiguration::getFirstHalfOfIp)
            .collect(Collectors.toSet());

        // Adding to LinkedHashSet to ensure full IP addresses are checked first
        final Collection<String> expandedIpAddresses = new LinkedHashSet<>(rawIpAddresses);
        expandedIpAddresses.addAll(halfIpAddresses);

        return expandedIpAddresses;
    }

    private static String getFirstHalfOfIp(final String ip) {
        final String[] parts = ip.split("\\.");
        return (parts.length >= 2) ? parts[0] + "." + parts[1] + "." : ip;
    }

    private static Path getOutputDirectory() {
        final String timeZone = getOrDefault("TIMEZONE", DEFAULT_TIMEZONE);
        final String outputDirectoryNameFormat = getOrDefault("OUTPUT_DIRECTORY_NAME_FORMAT", DEFAULT_OUTPUT_DIRECTORY_NAME_FORMAT);
        final String outputDirectoryParentPath = getOrDefault("OUTPUT_DIRECTORY_PARENT_PATH", DEFAULT_OUTPUT_DIRECTORY_PARENT_PATH);

        final LocalDate currentDate = LocalDate.now(ZoneId.of(timeZone));
        final String outputDirectoryName = currentDate.format(DateTimeFormatter.ofPattern(outputDirectoryNameFormat, Locale.getDefault()));
        return Paths.get(outputDirectoryParentPath, outputDirectoryName);
    }

    private static Path getInputFilePath() {
        return Paths.get(getOrDefault("TRACKER_INPUT_FILE_PATH", DEFAULT_TRACKER_INPUT_FILE_PATH));
    }

    private static boolean getBooleanEnvironmentVariable(final String environmentVariableName) {
        return Boolean.parseBoolean(getOrDefault(environmentVariableName, "false"));
    }

    private static String getOrDefault(final String environmentVariableName, final String defaultValue) {
        final String environmentVariable = System.getenv(environmentVariableName);
        if (environmentVariable != null) {
            return environmentVariable;
        }
        return defaultValue;
    }
}
