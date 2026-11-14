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
import java.io.Reader;
import java.io.StringReader;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import net.zodac.tracker.framework.exception.InvalidCsvInputException;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

/**
 * Utility class to read tracker credentials from CSV content (such as a file uploaded in the web UI), with the header row
 * {@code trackerName,username,password}. Rows starting with {@value #COMMENT_SYMBOL} are ignored.
 */
public final class TrackerCsvReader {

    /**
     * The character that marks a CSV row as a comment.
     */
    public static final char COMMENT_SYMBOL = '#';

    private static final String[] CSV_HEADERS = {"trackerName", "username", "password"};
    private static final CSVFormat CSV_FORMAT = CSVFormat.DEFAULT
        .builder()
        .setHeader(CSV_HEADERS)
        .setSkipHeaderRecord(false)
        .setCommentMarker(COMMENT_SYMBOL)
        .get();

    private TrackerCsvReader() {

    }

    /**
     * Reads the CSV content, and converts each row into a {@link TrackerCredential}. Rows for a tracker with no matching handler are not returned
     * as credentials, but are listed in {@link CsvContent#unsupportedTrackers()}.
     *
     * @param csvContent the CSV content
     * @return the {@link CsvContent}
     * @throws InvalidCsvInputException thrown if the content is empty, has no header row, or cannot be parsed
     * @see TrackerCredential#fromCsv(CSVRecord)
     */
    public static CsvContent read(final String csvContent) {
        final Set<TrackerCredential> trackerCredentials = extractTrackerCredentials(csvContent);

        final Set<String> unsupportedTrackers = trackerCredentials.stream()
            .map(TrackerCredential::name)
            .filter(name -> TrackerHandlerFactory.findMatchingHandler(name).isEmpty())
            .collect(Collectors.toCollection(TreeSet::new));

        final Set<TrackerCredential> supportedCredentials = new LinkedHashSet<>(trackerCredentials);
        supportedCredentials.removeIf(credential -> unsupportedTrackers.contains(credential.name()));
        return new CsvContent(supportedCredentials, unsupportedTrackers);
    }

    private static Set<TrackerCredential> extractTrackerCredentials(final String csvContent) {
        try (final Reader reader = new StringReader(csvContent);
            final CSVParser csvParser = CSV_FORMAT.parse(reader)
        ) {
            final List<CSVRecord> records = csvParser.getRecords();
            validateCsvContent(records);

            return records
                .stream()
                .skip(1) // Skip header row
                .map(TrackerCredential::fromCsv)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        } catch (final IOException | IllegalArgumentException | IllegalStateException e) {
            throw new InvalidCsvInputException("Unable to read the CSV file: %s".formatted(e.getMessage()), e);
        }
    }

    private static void validateCsvContent(final List<CSVRecord> records) {
        if (records.isEmpty()) {
            throw new InvalidCsvInputException("The CSV file is empty");
        }

        if (!isHeaderRow(records.getFirst())) {
            throw new InvalidCsvInputException("The CSV header row is missing, expected: '%s'".formatted(String.join(",", CSV_HEADERS)));
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

    /**
     * The trackers read from CSV content.
     *
     * @param credentials         the {@link TrackerCredential}s of the supported trackers
     * @param unsupportedTrackers the names of the trackers in the CSV content with no matching handler
     */
    public record CsvContent(Set<TrackerCredential> credentials, Set<String> unsupportedTrackers) {

    }
}
