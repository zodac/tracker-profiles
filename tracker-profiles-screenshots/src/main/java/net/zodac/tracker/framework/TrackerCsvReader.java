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

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Objects;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

/**
 * Utility class to read the {@link ApplicationConfiguration#trackerInputFilePath()} file.
 */
public final class TrackerCsvReader {

    private static final ApplicationConfiguration CONFIG = Configuration.get();
    private static final String[] CSV_HEADERS = {"trackerName", "username", "password"};
    private static final CSVFormat DEFAULT_FORMAT = CSVFormat.DEFAULT
        .builder()
        .setHeader(CSV_HEADERS)
        .setSkipHeaderRecord(true)
        .setCommentMarker(CONFIG.csvCommentSymbol())
        .get();

    private TrackerCsvReader() {

    }

    /**
     * Reads the input file {@link ApplicationConfiguration#trackerInputFilePath()}, and converts each row into a {@link TrackerDefinition}.
     *
     * @return the {@link List} of {@link TrackerDefinition}s
     * @throws IOException thrown if there is a problem reading the header or skipping the first record
     * @see TrackerDefinition#fromCsv(CSVRecord)
     */
    public static List<TrackerDefinition> readTrackerInfo() throws IOException {
        try (final InputStream inputStream = Files.newInputStream(CONFIG.trackerInputFilePath());
             final Reader reader = new InputStreamReader(Objects.requireNonNull(inputStream), StandardCharsets.UTF_8);
             final CSVParser csvParser = CSVParser.builder().setReader(reader).setFormat(DEFAULT_FORMAT).get()
        ) {
            return csvParser
                .stream()
                .map(TrackerDefinition::fromCsv)
                .toList();
        }
    }
}
