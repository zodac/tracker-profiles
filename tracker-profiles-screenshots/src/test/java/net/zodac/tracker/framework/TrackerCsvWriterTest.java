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

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link TrackerCsvWriter}.
 */
class TrackerCsvWriterTest {

    @Test
    void givenNoCredentials_whenWritten_thenOnlyHeaderRowIsWritten() {
        assertThat(TrackerCsvWriter.write(List.of()))
            .isEqualTo("trackerName,username,password\n");
    }

    @Test
    void givenCredentials_whenWritten_thenEachIsWrittenInOrder() {
        final List<TrackerCredential> credentials = List.of(
            new TrackerCredential("AnimeZ", "user", "pass"),
            new TrackerCredential("Aither", "other", "p,a\"ss")
        );

        final String expected = """
            trackerName,username,password
            AnimeZ,user,pass
            Aither,other,"p,a""ss"
            """;

        assertThat(TrackerCsvWriter.write(credentials))
            .isEqualTo(expected);
    }

    @Test
    void givenWrittenCredentials_whenRead_thenSameCredentialsAreReturned() {
        final List<TrackerCredential> credentials = List.of(
            new TrackerCredential("AnimeZ", "#user", "pass,with\"quotes"),
            new TrackerCredential("Aither", "other", "#password")
        );

        assertThat(TrackerCsvReader.read(TrackerCsvWriter.write(credentials)).credentials())
            .containsExactlyElementsOf(credentials);
    }
}
