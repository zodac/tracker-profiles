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

/**
 * A single log message captured by {@link WebLogAppender}, to be displayed in the web UI.
 *
 * @param id        a sequential identifier, increasing for each log message
 * @param timestamp the time of the log message, as epoch milliseconds
 * @param level     the log level
 * @param tracker   the name of the tracker that logged the message, or empty if it was not logged by a tracker
 * @param attempt   the attempt of the tracker the message was logged during, starting at 1, or 0 if it was not logged during an attempt
 * @param step      the name of the {@link net.zodac.tracker.framework.progress.TrackerStep} the message was logged during, or empty if none
 * @param section   the section of the step the message was logged during (such as the redaction type of a screenshot), or empty if none
 * @param message   the log message, with any ANSI escape codes removed
 */
public record LogEntry(long id, long timestamp, String level, String tracker, int attempt, String step, String section, String message) {

}
