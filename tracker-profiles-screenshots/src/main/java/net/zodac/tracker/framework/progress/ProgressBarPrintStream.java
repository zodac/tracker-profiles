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

package net.zodac.tracker.framework.progress;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.locks.Lock;

/**
 * A {@link PrintStream} wrapper that keeps a Clique progress bar pinned to the bottom of the console output.
 *
 * <p>
 * Install this as {@code System.out} before execution begins. Every write is intercepted:
 *
 * <ul>
 *   <li>Bar writes (identified by a leading carriage-return {@code \r}, as produced by Clique) are passed directly to the underlying
 *       stream so the bar overwrites itself in place.</li>
 *   <li>All other (log) writes first clear the current terminal line with {@code \r\u001B[2K} to remove any progress bar that
 *       is displayed there, then write the log content. After any write that ends with a newline {@code \n}, the progress bar is
 *       re-rendered via {@link #renderBar()}.</li>
 * </ul>
 *
 * <p>
 * This class is thread-safe. All writes are guarded by the {@link Lock} shared with {@link ProgressBarManager} (obtained via
 * {@link ProgressBarManager#getProgressBarLock()}), ensuring that bar-clearing, log output, and bar re-rendering are atomic across
 * concurrent threads. Because {@link java.util.concurrent.locks.ReentrantLock} permits re-entry, holding this lock while calling
 * {@link ProgressBarManager#getProgressBarContent()} (which also acquires it) is safe.
 */
public final class ProgressBarPrintStream extends PrintStream {

    private static final byte[] CLEAR_LINE = "\r\u001B[2K".getBytes(StandardCharsets.UTF_8);
    private static final PrintStream WRAPPED_STREAM = System.out;
    private static final char NEWLINE = '\n';

    private final ProgressBarManager progressBarManager;
    private final Lock progressBarLock;

    /**
     * Creates a {@link ProgressBarPrintStream} that wraps {@link System#out}.
     */
    public ProgressBarPrintStream(final ProgressBarManager progressBarManager) {
        super(WRAPPED_STREAM, true, StandardCharsets.UTF_8);
        this.progressBarManager = progressBarManager;
        this.progressBarLock = progressBarManager.getProgressBarLock();
    }

    @Override
    public void write(final int b) {
        write(new byte[] {(byte) b}, 0, 1);
    }

    @Override
    public void write(final byte[] buf, final int off, final int len) {
        if (len == 0) {
            return;
        }

        progressBarLock.lock();
        try {
            final boolean isBarWrite = buf[off] == '\r';
            if (isBarWrite) {
                // Replace Clique's partial bar with the full bar (including X/Y tracker suffix) so the cursor position is consistent
                // If Clique's write ends with \n (bar completed), forward that newline so close() can correctly detect isDone().
                renderBar();
                if (buf[off + len - 1] == NEWLINE) {
                    WRAPPED_STREAM.write(NEWLINE);
                    WRAPPED_STREAM.flush();
                }
                return;
            }

            if (progressBarManager.isActive()) {
                WRAPPED_STREAM.write(CLEAR_LINE, 0, CLEAR_LINE.length);
            }

            WRAPPED_STREAM.write(buf, off, len);

            if (buf[off + len - 1] == '\n' && progressBarManager.isActive()) {
                renderBar();
            }
        } finally {
            progressBarLock.unlock();
        }
    }

    private void renderBar() {
        final byte[] barBytes = ("\r" + progressBarManager.getProgressBarContent()).getBytes(StandardCharsets.UTF_8);
        WRAPPED_STREAM.write(barBytes, 0, barBytes.length);
        WRAPPED_STREAM.flush();
    }

    @Override
    public void close() {
        // super.close(); // Don't call super.close() or else it will close System.out and prevent any further logging
        if (progressBarManager.isActive()) {
            // Bar is mid-progress: no newline was emitted, so the cursor is still on the bar line; clear it in place
            final byte[] clearBytes = "\r\u001B[2K".getBytes(StandardCharsets.UTF_8);
            WRAPPED_STREAM.write(clearBytes, 0, clearBytes.length);
        } else if (progressBarManager.isDone()) {
            // Bar reached 100%: Clique emitted a newline leaving the cursor on the line below the bar; move up then clear
            final byte[] clearBytes = "\u001B[1A\r\u001B[2K".getBytes(StandardCharsets.UTF_8);
            WRAPPED_STREAM.write(clearBytes, 0, clearBytes.length);
        }

        // Stop the bar so that any logging after close (e.g. result summary) does not re-render it
        progressBarManager.stop();
        WRAPPED_STREAM.flush();
    }
}
