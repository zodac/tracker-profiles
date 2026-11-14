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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import net.zodac.tracker.framework.exception.InvalidCsvInputException;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link TrackerCsvReader}.
 */
class TrackerCsvReaderTest {

    @Test
    void givenValidCsv_whenRead_thenSupportedTrackersAreReturned() {
        final String csv = """
            trackerName,username,password
            # Anthelion,ignored,ignored
            animez,user, pass
            NotARealTracker,user,pass
            """;

        final TrackerCsvReader.CsvContent csvContent = TrackerCsvReader.read(csv);

        assertThat(csvContent.credentials())
            .containsExactly(new TrackerCredential("animez", "user", "pass"));
        assertThat(csvContent.unsupportedTrackers())
            .containsExactly("NotARealTracker");
    }

    @Test
    void givenCsvWithoutHeader_whenRead_thenExceptionIsThrown() {
        final String csv = "AnimeZ,user,pass\n";

        assertThatThrownBy(() -> TrackerCsvReader.read(csv))
            .isInstanceOf(InvalidCsvInputException.class)
            .hasMessageContaining("header row");
    }

    @Test
    void givenEmptyCsv_whenRead_thenExceptionIsThrown() {
        assertThatThrownBy(() -> TrackerCsvReader.read("# Only a comment\n"))
            .isInstanceOf(InvalidCsvInputException.class)
            .hasMessageContaining("empty");
    }

    @Test
    void givenRowWithMissingColumns_whenRead_thenExceptionIsThrown() {
        final String csv = """
            trackerName,username,password
            AnimeZ,user
            """;

        assertThatThrownBy(() -> TrackerCsvReader.read(csv))
            .isInstanceOf(InvalidCsvInputException.class);
    }
}
