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

package net.zodac.tracker.web.log;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.impl.Log4jLogEvent;
import org.apache.logging.log4j.message.SimpleMessage;
import org.apache.logging.log4j.util.SortedArrayStringMap;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link WebLogAppender}.
 */
class WebLogAppenderTest {

    @Test
    void givenMessageWithAnsiCodes_whenStripped_thenPlainTextIsReturned() {
        assertThat(WebLogAppender.stripAnsiCodes("\u001B[31mred\u001B[0m and \u001B[1;34mblue\u001B[m"))
            .isEqualTo("red and blue");
    }

    @Test
    void givenMessagesFromTrackers_whenRetrieved_thenEachTrackerHasItsOwnMessages() {
        final WebLogAppender webLogAppender = WebLogAppender.install();
        webLogAppender.clear();
        webLogAppender.append(logEvent(Level.INFO, "\u001B[32mStarting execution\u001B[0m", null));
        webLogAppender.append(logEvent(Level.INFO, "\t- Opening tracker", "First"));
        webLogAppender.append(logEvent(Level.DEBUG, "Debug detail", "Second"));
        webLogAppender.append(logEvent(Level.INFO, "\t- Logging in as 'user'", "First"));

        assertThat(webLogAppender.entries("First", 0L))
            .extracting(LogEntry::message)
            .containsExactly("\t- Opening tracker", "\t- Logging in as 'user'");
        assertThat(webLogAppender.entries("Second", 0L))
            .singleElement()
            .returns("DEBUG", LogEntry::level)
            .returns("Second", LogEntry::tracker);
        assertThat(webLogAppender.entries(null, 0L))
            .singleElement()
            .returns("", LogEntry::tracker)
            .returns("Starting execution", LogEntry::message);
    }

    @Test
    void givenMessages_whenRetrievedAfterAnId_thenOnlyLaterMessagesAreReturned() {
        final WebLogAppender webLogAppender = WebLogAppender.install();
        webLogAppender.clear();
        webLogAppender.append(logEvent(Level.INFO, "first", "Tracker"));
        final long firstId = webLogAppender.entries("Tracker", 0L).getFirst().id();
        webLogAppender.append(logEvent(Level.WARN, "second", "Tracker"));

        assertThat(webLogAppender.entries("Tracker", firstId))
            .extracting(LogEntry::message)
            .containsExactly("second");
    }

    @Test
    void givenMessages_whenLatestMessagesRetrieved_thenLatestInfoMessageOfEachTrackerIsSummarised() {
        final WebLogAppender webLogAppender = WebLogAppender.install();
        webLogAppender.clear();
        webLogAppender.append(logEvent(Level.INFO, "\t\t- Opening user profile page", "Tracker"));
        webLogAppender.append(logEvent(Level.DEBUG, "Ignored, since below INFO", "Tracker"));

        assertThat(webLogAppender.latestMessages())
            .containsExactly(Map.entry("Tracker", "Opening user profile page"));
    }

    @Test
    void givenMessages_whenCleared_thenNoMessagesAreRetained() {
        final WebLogAppender webLogAppender = WebLogAppender.install();
        webLogAppender.append(logEvent(Level.INFO, "message", "Tracker"));

        webLogAppender.clear();

        assertThat(webLogAppender.entries("Tracker", 0L))
            .isEmpty();
        assertThat(webLogAppender.latestMessages())
            .isEmpty();
    }

    private static Log4jLogEvent logEvent(final Level level, final String message, final @Nullable String tracker) {
        final SortedArrayStringMap contextData = new SortedArrayStringMap();
        if (tracker != null) {
            contextData.putValue("tracker", tracker);
        }

        return Log4jLogEvent.newBuilder()
            .setLevel(level)
            .setMessage(new SimpleMessage(message))
            .setContextData(contextData)
            .setTimeMillis(System.currentTimeMillis())
            .build();
    }
}
