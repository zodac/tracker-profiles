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
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.config.Property;

/**
 * A Log4j2 appender that retains the most recent log messages in memory and forwards new messages to subscribers, so they can be streamed to the
 * web UI.
 */
public final class WebLogAppender extends AbstractAppender {

    private static final String APPENDER_NAME = "WEB_UI";
    private static final int MAXIMUM_RETAINED_ENTRIES = 5_000;
    private static final Pattern ANSI_ESCAPE_CODE = Pattern.compile("\u001B\\[[0-9;]*[A-Za-z]");

    private final Lock lock = new ReentrantLock();
    private final Deque<LogEntry> retainedEntries = new ArrayDeque<>();
    private final Set<Consumer<LogEntry>> subscribers = new CopyOnWriteArraySet<>();
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
        final String tracker = event.getContextData().getValue("tracker");
        final LogEntry logEntry;

        lock.lock();
        try {
            final long id = nextId;
            nextId++;
            logEntry = new LogEntry(
                id,
                event.getTimeMillis(),
                event.getLevel().name(),
                tracker == null ? "" : stripAnsiCodes(tracker),
                formatMessage(event)
            );
            retainedEntries.addLast(logEntry);
            if (retainedEntries.size() > MAXIMUM_RETAINED_ENTRIES) {
                retainedEntries.removeFirst();
            }
        } finally {
            lock.unlock();
        }

        for (final Consumer<LogEntry> subscriber : subscribers) {
            subscriber.accept(logEntry);
        }
    }

    /**
     * Registers a subscriber to receive each new {@link LogEntry}, and returns all currently retained entries. Registration and retrieval are
     * atomic, so no entry is missed or duplicated between the returned entries and those sent to the subscriber.
     *
     * <p>
     * The subscriber is called on the logging thread, so it must not block.
     *
     * @param subscriber the subscriber to receive new {@link LogEntry}s
     * @return the currently retained {@link LogEntry}s, oldest first
     */
    public List<LogEntry> subscribe(final Consumer<LogEntry> subscriber) {
        lock.lock();
        try {
            subscribers.add(subscriber);
            return List.copyOf(retainedEntries);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Removes a subscriber previously registered with {@link #subscribe(Consumer)}.
     *
     * @param subscriber the subscriber to remove
     */
    public void unsubscribe(final Consumer<LogEntry> subscriber) {
        subscribers.remove(subscriber);
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
