package me.zodac.tracker.framework;

import java.io.IOException;
import java.io.Reader;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;

public final class TrackerCsvReader {

    private static final String CSV_FILE_NAME = "trackers.csv";
    private static final String[] CSV_HEADERS = {"name", "loginLink", "profilePage", "username", "password"};
    private static final CSVFormat DEFAULT_FORMAT = CSVFormat.DEFAULT
        .builder()
        .setHeader(CSV_HEADERS)
        .setSkipHeaderRecord(true)
        .setCommentMarker('#')
        .build();

    private TrackerCsvReader() {

    }

    public static List<TrackerInfo> readTrackerInfo() {
        try (
            final Reader reader = Files.newBufferedReader(Paths.get(ClassLoader.getSystemResource(CSV_FILE_NAME).toURI()));
            final CSVParser csvParser = new CSVParser(reader, DEFAULT_FORMAT)
        ) {
            return csvParser
                .stream()
                .map(TrackerInfo::fromCsv)
                .toList();
        } catch (final IOException | URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }
}
