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
import java.util.Arrays;
import java.util.Collection;
import java.util.Properties;
import java.util.regex.Pattern;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Utility file that loads configuration detail from the {@code config.properties} file.
 */
public record ConfigurationProperties(
    Collection<String> emailAddresses,
    Collection<String> ipAddresses,
    String outputDirectoryPath,
    boolean previewTrackerScreenshot,
    boolean useHeadlessBrowser
) {

    private static final Logger LOGGER = LogManager.getLogger();
    private static final String PROPRTIES_FILE_NAME = "config.properties";
    private static final String DEFAULT_OUTPUT_DIRECTORY_PATH = "./screenshots";
    private static final Pattern COMMA_SEPARATED_VALUES_PATTERN = Pattern.compile("\\s*,\\s*");

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

            final var configurationProperties = new ConfigurationProperties(
                getCommaSeparatedStringProperty(properties, "emailAddresses"),
                getCommaSeparatedStringProperty(properties, "ipAddresses"),
                properties.getProperty("outputDirectoryPath", DEFAULT_OUTPUT_DIRECTORY_PATH),
                getBooleanProperty(properties, "previewTrackerScreenshot"),
                getBooleanProperty(properties, "useHeadlessBrowser")
            );
            LOGGER.debug("Loaded properties: {}", configurationProperties);
            LOGGER.debug("");
            return configurationProperties;
        } catch (final Exception e) {
            throw new IllegalStateException(String.format("Unable to load properties from '%s'", PROPRTIES_FILE_NAME));
        }
    }

    private static boolean getBooleanProperty(final Properties properties, final String propertyName) {
        return Boolean.parseBoolean(properties.getProperty(propertyName, "false"));
    }

    private static Collection<String> getCommaSeparatedStringProperty(final Properties properties, final String propertyName) {
        final String value = properties.getProperty(propertyName, "");
        return Arrays.asList(COMMA_SEPARATED_VALUES_PATTERN.split(value));
    }
}
