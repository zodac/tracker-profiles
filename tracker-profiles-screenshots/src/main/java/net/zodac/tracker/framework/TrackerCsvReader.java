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

package net.zodac.tracker.framework;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import net.zodac.tracker.framework.config.ApplicationConfiguration;
import net.zodac.tracker.framework.config.Configuration;
import net.zodac.tracker.framework.exception.InvalidCsvInputException;
import net.zodac.tracker.util.StringUtils;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Utility class to read the {@link ApplicationConfiguration#trackerInputFilePath()} file.
 */
public final class TrackerCsvReader {

    private static final ApplicationConfiguration CONFIG = Configuration.get();
    private static final Logger LOGGER = LogManager.getLogger();
    private static final String[] CSV_HEADERS = {"trackerName", "username", "password"};
    private static final CSVFormat DEFAULT_FORMAT = CSVFormat.DEFAULT
        .builder()
        .setHeader(CSV_HEADERS)
        .setSkipHeaderRecord(false)
        .setCommentMarker(CONFIG.csvCommentSymbol())
        .get();

    private TrackerCsvReader() {

    }

    /**
     * Reads the input file {@link ApplicationConfiguration#trackerInputFilePath()}, and converts each row into a {@link TrackerCredential}.
     *
     * @return the {@link Set} of {@link TrackerCredential}s
     * @throws IOException thrown if there is a problem reading the file or skipping the first record
     * @see TrackerCredential#fromCsv(CSVRecord)
     */
    public static Set<TrackerCredential> readTrackerCredentials() throws IOException {
        final Path csvPath = CONFIG.trackerInputFilePath();
        validateFilePath(csvPath);

        final Set<TrackerCredential> trackerCredentials = extractTrackerCredentials(csvPath);
        return validateCredentials(trackerCredentials);
    }

    private static Set<TrackerCredential> validateCredentials(final Set<TrackerCredential> trackerCredentials) {
        final Set<String> trackersWithNoHandlers = trackerCredentials.stream()
            .map(TrackerCredential::name)
            .filter(name -> TrackerHandlerFactory.findMatchingHandler(name).isEmpty())
            .collect(Collectors.toCollection(TreeSet::new));

        if (!trackersWithNoHandlers.isEmpty()) {
            final String plural = StringUtils.pluralise(trackersWithNoHandlers);
            if (CONFIG.failOnUnsupportedTracker()) {
                throw new IllegalArgumentException("Unknown tracker%s in CSV: %s".formatted(plural, trackersWithNoHandlers));
            } else {
                LOGGER.debug("Unknown tracker{} in CSV: {}", plural, trackersWithNoHandlers);
            }
        }

        final Set<TrackerCredential> result = new LinkedHashSet<>(trackerCredentials);
        result.removeIf(credential -> trackersWithNoHandlers.contains(credential.name()));
        return result;
    }

    private static Set<TrackerCredential> extractTrackerCredentials(final Path csvPath) throws IOException {
        try (final InputStream inputStream = Files.newInputStream(CONFIG.trackerInputFilePath());
            final Reader reader = new InputStreamReader(Objects.requireNonNull(inputStream), StandardCharsets.UTF_8);
            final CSVParser csvParser = DEFAULT_FORMAT.parse(reader)
        ) {
            // Ensure file isn't empty
            final List<CSVRecord> records = csvParser.getRecords();
            validateCsvFileContent(records, csvPath);

            return records
                .stream()
                .skip(1) // Skip header row
                .map(TrackerCredential::fromCsv)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        }
    }

    private static void validateFilePath(final Path csvPath) {
        if (!Files.exists(csvPath)) {
            throw new InvalidCsvInputException("Tracker CSV file '%s' does not exist".formatted(csvPath.toAbsolutePath()));
        }

        if (!Files.isRegularFile(csvPath)) {
            throw new InvalidCsvInputException("Tracker CSV path '%s' is not a file".formatted(csvPath.toAbsolutePath()));
        }
    }

    private static void validateCsvFileContent(final List<CSVRecord> records, final Path csvPath) {
        if (records.isEmpty()) {
            throw new InvalidCsvInputException("CSV file '%s' is empty".formatted(csvPath.toAbsolutePath()));
        }

        // Validate header row
        final CSVRecord firstRecord = records.getFirst();
        if (!isHeaderRow(firstRecord)) {
            throw new InvalidCsvInputException(
                "CSV header row in '%s' is missing, expected: '%s'".formatted(csvPath.toAbsolutePath(), String.join(",", CSV_HEADERS)));
        }
    }

    private static boolean isHeaderRow(final CSVRecord csvRecord) {
        if (csvRecord.size() != CSV_HEADERS.length) {
            return false;
        }

        for (int i = 0; i < CSV_HEADERS.length; i++) {
            if (!CSV_HEADERS[i].equalsIgnoreCase(csvRecord.get(i).trim())) {
                return false;
            }
        }

        return true;
    }
}
