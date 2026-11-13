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

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
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
    void givenRetainedEntries_whenSubscribed_thenRetainedEntriesAreReturnedAndNewEntriesAreSent() {
        final WebLogAppender webLogAppender = WebLogAppender.install();
        webLogAppender.append(logEvent(Level.INFO, "\u001B[32mfirst\u001B[0m", "[Tracker] "));

        final List<LogEntry> received = new ArrayList<>();
        final Consumer<LogEntry> subscriber = received::add;
        final List<LogEntry> retained = webLogAppender.subscribe(subscriber);
        webLogAppender.append(logEvent(Level.WARN, "second", null));
        webLogAppender.unsubscribe(subscriber);
        webLogAppender.append(logEvent(Level.ERROR, "third", null));

        assertThat(retained)
            .last()
            .returns("INFO", LogEntry::level)
            .returns("[Tracker] ", LogEntry::tracker)
            .returns("first", LogEntry::message);
        assertThat(received)
            .singleElement()
            .returns("WARN", LogEntry::level)
            .returns("", LogEntry::tracker)
            .returns("second", LogEntry::message);
        assertThat(received.getFirst().id())
            .isGreaterThan(retained.getLast().id());
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
