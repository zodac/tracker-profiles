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

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Pattern;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.config.Property;
import org.jspecify.annotations.Nullable;

/**
 * A Log4j2 appender that keeps the log messages of the current execution in memory, separately for each tracker, so the web UI can show the logs of
 * a single tracker (trackers are executed in parallel, so their messages are interleaved). Messages logged outside a tracker (such as the start and
 * summary of an execution) are kept as the execution log.
 *
 * <p>
 * The tracker of a message is read from the {@code tracker} key of the Log4j {@link org.apache.logging.log4j.ThreadContext}, which is set for the
 * thread executing each tracker. All messages are cleared with {@link #clear()} when a new execution starts.
 */
public final class WebLogAppender extends AbstractAppender {

    /**
     * The key in the logging context holding the name of the tracker being screenshot.
     */
    public static final String TRACKER_CONTEXT_KEY = "tracker";

    private static final String APPENDER_NAME = "WEB_UI";
    private static final String EXECUTION_LOG = "";
    private static final int MAXIMUM_ENTRIES_PER_LOG = 2_000;
    private static final Pattern ANSI_ESCAPE_CODE = Pattern.compile("\u001B\\[[0-9;]*[A-Za-z]");
    private static final Pattern LEADING_LIST_MARKER = Pattern.compile("^[\\s>-]+");

    private final Lock lock = new ReentrantLock();
    private final Map<String, Deque<LogEntry>> entriesByTracker = new HashMap<>();
    private final Map<String, String> latestMessageByTracker = new HashMap<>();
    private long nextId = 1L;

    private WebLogAppender() {
        super(APPENDER_NAME, null, null, true, Property.EMPTY_ARRAY);
    }

    /**
     * Creates a {@link WebLogAppender} and attaches it to every configured logger of the current {@link LoggerContext}.
     *
     * @return the installed {@link WebLogAppender}
     */
    public static WebLogAppender install() {
        final WebLogAppender appender = new WebLogAppender();
        appender.start();

        final LoggerContext loggerContext = (LoggerContext) LogManager.getContext(false); // NOPMD: CloseResource - shared application context
        final org.apache.logging.log4j.core.config.Configuration loggingConfiguration = loggerContext.getConfiguration();
        loggingConfiguration.addAppender(appender);
        for (final LoggerConfig loggerConfig : loggingConfiguration.getLoggers().values()) {
            attach(appender, loggerConfig);
        }
        attach(appender, loggingConfiguration.getRootLogger());
        loggerContext.updateLoggers();
        return appender;
    }

    private static void attach(final WebLogAppender appender, final LoggerConfig loggerConfig) {
        if (!loggerConfig.getAppenders().containsKey(APPENDER_NAME)) {
            loggerConfig.addAppender(appender, null, null);
        }
    }

    @Override
    public void append(final LogEvent event) {
        final String trackerValue = event.getContextData().getValue(TRACKER_CONTEXT_KEY);
        final String tracker = trackerValue == null ? EXECUTION_LOG : stripAnsiCodes(trackerValue).trim();
        final String message = formatMessage(event);

        lock.lock();
        try {
            final long id = nextId;
            nextId++;
            final Deque<LogEntry> entries = entriesByTracker.computeIfAbsent(tracker, _ -> new ArrayDeque<>());
            entries.addLast(new LogEntry(id, event.getTimeMillis(), event.getLevel().name(), tracker, message));
            if (entries.size() > MAXIMUM_ENTRIES_PER_LOG) {
                entries.removeFirst();
            }

            final String summary = summarise(message);
            if (!tracker.isEmpty() && event.getLevel().isMoreSpecificThan(Level.INFO) && !summary.isEmpty()) {
                latestMessageByTracker.put(tracker, summary);
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Retrieves the retained log messages of a tracker, or of the execution itself, written after the given ID.
     *
     * @param tracker the name of the tracker, or {@code null}/empty for the messages not logged by a tracker
     * @param afterId only messages with a greater {@link LogEntry#id()} are returned (0 for all messages)
     * @return the {@link LogEntry}s, oldest first
     */
    public List<LogEntry> entries(final @Nullable String tracker, final long afterId) {
        lock.lock();
        try {
            final Deque<LogEntry> entries = entriesByTracker.get(tracker == null ? EXECUTION_LOG : tracker);
            if (entries == null) {
                return List.of();
            }
            return entries.stream()
                .filter(entry -> entry.id() > afterId)
                .toList();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Retrieves the most recent message of at least {@code INFO} level logged by each tracker, used to summarise what each tracker is doing.
     *
     * @return the latest message, keyed by tracker name
     */
    public Map<String, String> latestMessages() {
        lock.lock();
        try {
            return Map.copyOf(latestMessageByTracker);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Removes every retained log message, when a new execution starts.
     */
    public void clear() {
        lock.lock();
        try {
            entriesByTracker.clear();
            latestMessageByTracker.clear();
        } finally {
            lock.unlock();
        }
    }

    private static String formatMessage(final LogEvent event) {
        final String message = stripAnsiCodes(event.getMessage().getFormattedMessage());
        final Throwable thrown = event.getThrown();
        if (thrown == null) {
            return message;
        }

        final StringWriter stackTrace = new StringWriter();
        thrown.printStackTrace(new PrintWriter(stackTrace));
        return message + System.lineSeparator() + stackTrace;
    }

    // The first line of the message, without the indentation and list markers used in the console output
    private static String summarise(final String message) {
        final String firstLine = message.lines().findFirst().orElse("");
        return LEADING_LIST_MARKER.matcher(firstLine).replaceFirst("").trim();
    }

    /**
     * Removes any ANSI escape codes (used for console colours) from the input.
     *
     * @param input the input {@link String}
     * @return the input without ANSI escape codes
     */
    static String stripAnsiCodes(final String input) {
        return ANSI_ESCAPE_CODE.matcher(input).replaceAll("");
    }
}
