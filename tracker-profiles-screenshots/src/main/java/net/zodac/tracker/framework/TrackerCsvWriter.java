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
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.util.Collection;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

/**
 * Writes tracker credentials as a CSV file, in the format read by {@link TrackerCsvReader}.
 */
public final class TrackerCsvWriter {

    private static final CSVFormat CSV_FORMAT = CSVFormat.DEFAULT
        .builder()
        .setHeader(TrackerCsvReader.headers().toArray(String[]::new))
        .setRecordSeparator('\n')
        .get();

    private TrackerCsvWriter() {

    }

    /**
     * Writes the {@link TrackerCredential}s as a CSV file, with the header row. With no {@link TrackerCredential}s, only the header row is written,
     * which can be used as a sample of the format.
     *
     * @param trackerCredentials the {@link TrackerCredential}s to write, in order
     * @return the content of the CSV file
     */
    public static String write(final Collection<TrackerCredential> trackerCredentials) {
        final StringWriter writer = new StringWriter();
        try (final CSVPrinter csvPrinter = CSV_FORMAT.print(writer)) {
            for (final TrackerCredential trackerCredential : trackerCredentials) {
                csvPrinter.printRecord(trackerCredential.name(), trackerCredential.username(), trackerCredential.password());
            }
        } catch (final IOException e) {
            throw new UncheckedIOException("Unable to write the CSV file", e);
        }
        return writer.toString();
    }
}
