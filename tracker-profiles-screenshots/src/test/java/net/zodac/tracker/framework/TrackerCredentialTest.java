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

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.StringReader;
import java.util.List;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link TrackerCredential}.
 */
class TrackerCredentialTest {

    @Test
    void givenSameNameDifferentCase_whenCompareTo_thenZeroReturned() {
        final TrackerCredential first = new TrackerCredential("MyTracker", "user1", "pass1");
        final TrackerCredential second = new TrackerCredential("mytracker", "user2", "pass2");

        assertThat(first)
            .isEqualByComparingTo(second);
    }

    @Test
    void givenAlphabeticallyEarlierName_whenCompareTo_thenNegativeReturned() {
        final TrackerCredential first = new TrackerCredential("ATracker", "user", "pass");
        final TrackerCredential second = new TrackerCredential("BTracker", "user", "pass");

        assertThat(first)
            .isLessThan(second);
    }

    @Test
    void givenAlphabeticallyLaterName_whenCompareTo_thenPositiveReturned() {
        final TrackerCredential first = new TrackerCredential("ZTracker", "user", "pass");
        final TrackerCredential second = new TrackerCredential("ATracker", "user", "pass");

        assertThat(first)
            .isGreaterThan(second);
    }

    @Test
    void givenCredential_whenToString_thenPasswordIsMasked() {
        final TrackerCredential credential = new TrackerCredential("MyTracker", "user", "secret");

        assertThat(credential)
            .hasToString("TrackerCredential[name=MyTracker, username=user, password=****]");
    }

    @Test
    void givenCsvRecord_whenFromCsv_thenFieldsAreTrimmedAndAssigned() throws Exception {
        final CSVRecord csvRecord = parseSingleRecord();

        final TrackerCredential credential = TrackerCredential.fromCsv(csvRecord);

        assertThat(credential.name()).isEqualTo("MyTracker");
        assertThat(credential.username()).isEqualTo("myUser");
        assertThat(credential.password()).isEqualTo("myPassword");
    }

    private static CSVRecord parseSingleRecord() throws IOException {
        final CSVFormat format = CSVFormat.DEFAULT.builder()
            .setHeader("trackerName", "username", "password")
            .setSkipHeaderRecord(false)
            .get();
        try (final CSVParser parser = format.parse(new StringReader("MyTracker,myUser,myPassword"))) {
            final List<CSVRecord> records = parser.getRecords();
            return records.getFirst();
        }
    }
}
