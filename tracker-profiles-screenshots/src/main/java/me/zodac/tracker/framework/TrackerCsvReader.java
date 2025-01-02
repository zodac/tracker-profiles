/*
 * BSD Zero Clause License
 *
 * Copyright (c) 2021-2025 zodac.me
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

import java.io.IOException;
import java.io.Reader;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

/**
 * Utility class to read the {@code trackers.csv} input file.
 */
public final class TrackerCsvReader {

    private static final ConfigurationProperties CONFIG = Configuration.get();
    private static final String CSV_FILE_NAME = "trackers.csv";
    private static final String[] CSV_HEADERS = {"trackerName", "loginLink", "profilePage", "username", "password", "manual"};
    private static final CSVFormat DEFAULT_FORMAT = CSVFormat.DEFAULT
        .builder()
        .setHeader(CSV_HEADERS)
        .setSkipHeaderRecord(true)
        .setCommentMarker(CONFIG.csvCommentSymbol())
        .build();

    private TrackerCsvReader() {

    }

    /**
     * Reads the input file {@value CSV_FILE_NAME}, and converts each row into a {@link TrackerDefinition}.
     *
     * @return the {@link List} of {@link TrackerDefinition}s
     * @throws URISyntaxException thrown if the {@value #CSV_FILE_NAME} resource URL is malformed
     * @throws IOException        throw if there is a problem reading the header or skipping the first record
     * @see TrackerDefinition#fromCsv(CSVRecord)
     */
    public static List<TrackerDefinition> readTrackerInfo() throws IOException, URISyntaxException {
        try (
            final Reader reader = Files.newBufferedReader(Paths.get(ClassLoader.getSystemResource(CSV_FILE_NAME).toURI()));
            final CSVParser csvParser = new CSVParser(reader, DEFAULT_FORMAT)
        ) {
            return csvParser
                .stream()
                .map(TrackerDefinition::fromCsv)
                .toList();
        }
    }
}
