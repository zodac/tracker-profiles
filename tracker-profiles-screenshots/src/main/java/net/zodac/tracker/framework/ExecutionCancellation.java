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

import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import net.zodac.tracker.framework.exception.ExecutionCancelledException;

/**
 * Tracks whether the current execution has been cancelled (from the web UI), so that long-running work can stop at the next checkpoint instead of
 * starting new work, such as another tracker, another retry attempt, or a new browser.
 *
 * <p>
 * Cancelling only sets a flag; the caller is responsible for also stopping work that is already in progress (by quitting the browsers and
 * interrupting the execution thread).
 *
 * <p>
 * A single tracker can also be cancelled (see {@link #requestTrackerCancellation(String)}), which stops only that tracker: it starts no new
 * attempt or browser, and the caller quits its browser. The tracker then fails, while the rest of the execution continues.
 */
public final class ExecutionCancellation {

    private static final AtomicBoolean CANCELLED = new AtomicBoolean(false);
    // Lower-case names, since tracker names are case-insensitive
    private static final Set<String> CANCELLED_TRACKERS = ConcurrentHashMap.newKeySet();

    private ExecutionCancellation() {

    }

    /**
     * Requests that the current execution is cancelled.
     */
    public static void requestCancellation() {
        CANCELLED.set(true);
    }

    /**
     * Clears any cancellation request, before a new execution is started.
     */
    public static void reset() {
        CANCELLED.set(false);
        CANCELLED_TRACKERS.clear();
    }

    /**
     * Requests that a single tracker of the current execution is cancelled, leaving the other trackers running.
     *
     * @param trackerName the name of the tracker (case-insensitive)
     */
    public static void requestTrackerCancellation(final String trackerName) {
        CANCELLED_TRACKERS.add(trackerName.toLowerCase(Locale.ROOT));
    }

    /**
     * Whether a single tracker has been cancelled (see {@link #requestTrackerCancellation(String)}), rather than the whole execution.
     *
     * @param trackerName the name of the tracker (case-insensitive)
     * @return {@code true} if cancellation of the tracker has been requested
     */
    public static boolean isTrackerCancelled(final String trackerName) {
        return CANCELLED_TRACKERS.contains(trackerName.toLowerCase(Locale.ROOT));
    }

    /**
     * Whether the work of a tracker is expected to fail, because the execution or the tracker itself has been cancelled.
     *
     * @param trackerName the name of the tracker (case-insensitive)
     * @return {@code true} if the execution or the tracker has been cancelled
     */
    public static boolean isCancelled(final String trackerName) {
        return isCancelled() || isTrackerCancelled(trackerName);
    }

    /**
     * Whether the current execution has been cancelled.
     *
     * @return {@code true} if cancellation has been requested
     */
    public static boolean isCancelled() {
        return CANCELLED.get();
    }

    /**
     * Checkpoint to stop work once the current execution has been cancelled.
     *
     * @throws ExecutionCancelledException thrown if cancellation has been requested
     */
    public static void throwIfCancelled() {
        if (isCancelled()) {
            throw new ExecutionCancelledException();
        }
    }

    /**
     * Checkpoint to stop the work of a tracker once the execution or the tracker has been cancelled.
     *
     * @param trackerName the name of the tracker (case-insensitive)
     * @throws ExecutionCancelledException thrown if cancellation of the execution or the tracker has been requested
     */
    public static void throwIfCancelled(final String trackerName) {
        if (isCancelled(trackerName)) {
            throw new ExecutionCancelledException();
        }
    }
}
