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

import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collection;
import java.util.Locale;
import java.util.Properties;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Utility file that loads configuration detail from the {@code config.properties} file.
 *
 * @param emailAddresses           a {@link Collection} of email addresses to be redacted from screenshots
 * @param ipAddresses              a {@link Collection} of IP addresses to be redacted from screenshots
 * @param outputDirectory          the output {@link Path} to the directory within which the screenshots will be saved
 * @param previewTrackerScreenshot whether the screenshot should be previewed during execution
 * @param useHeadlessBrowser       whether to use a headless browser or not
 */
public record ConfigurationProperties(
    Collection<String> emailAddresses,
    Collection<String> ipAddresses,
    Path outputDirectory,
    boolean previewTrackerScreenshot,
    boolean useHeadlessBrowser
) {

    private static final Logger LOGGER = LogManager.getLogger();
    private static final String PROPRTIES_FILE_NAME = "config.properties";

    // Default values
    private static final String DEFAULT_OUTPUT_DIRECTORY_NAME_FORMAT = "yyyy-MM-dd";
    private static final String DEFAULT_OUTPUT_DIRECTORY_PARENT_PATH = "./screenshots";
    private static final String DEFAULT_TIMEZONE = "UTC";

    /**
     * Loads the properties configured in the {@link #PROPRTIES_FILE_NAME} file.
     *
     * @return the {@link ConfigurationProperties}
     */
    public static ConfigurationProperties loadProperties() {
        final Properties properties = new Properties();
        LOGGER.debug("Loading properties from [{}]", PROPRTIES_FILE_NAME);

        try (final InputStream inputStream = Thread.currentThread().getContextClassLoader().getResourceAsStream(PROPRTIES_FILE_NAME)) {
            properties.load(inputStream);

            final ConfigurationProperties configurationProperties = new ConfigurationProperties(
                getCommaSeparatedStringProperty(properties, "emailAddresses"),
                getCommaSeparatedStringProperty(properties, "ipAddresses"),
                getOutputDirectory(properties),
                getBooleanProperty(properties, "previewTrackerScreenshot"),
                getBooleanProperty(properties, "useHeadlessBrowser")
            );
            LOGGER.debug("Loaded properties: {}", configurationProperties);
            LOGGER.debug("");
            return configurationProperties;
        } catch (final Exception e) {
            throw new IllegalStateException(String.format("Unable to load properties from '%s'", PROPRTIES_FILE_NAME), e);
        }
    }

    private static Path getOutputDirectory(final Properties properties) {
        final String timeZone = properties.getProperty("timeZone", DEFAULT_TIMEZONE);
        final String outputDirectoryNameFormat = properties.getProperty("outputDirectoryNameFormat", DEFAULT_OUTPUT_DIRECTORY_NAME_FORMAT);
        final String outputDirectoryParentPath = properties.getProperty("outputDirectoryParentPath", DEFAULT_OUTPUT_DIRECTORY_PARENT_PATH);

        final LocalDate currentDate = LocalDate.now(ZoneId.of(timeZone));
        final String outputDirectoryName = currentDate.format(DateTimeFormatter.ofPattern(outputDirectoryNameFormat, Locale.getDefault()));
        return Paths.get(outputDirectoryParentPath, outputDirectoryName);
    }

    private static boolean getBooleanProperty(final Properties properties, final String propertyName) {
        return Boolean.parseBoolean(properties.getProperty(propertyName, "false"));
    }

    private static Collection<String> getCommaSeparatedStringProperty(final Properties properties, final String propertyName) {
        final String value = properties.getProperty(propertyName, "");
        return Arrays.asList(value.split(","));
    }
}
