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

package net.zodac.tracker.framework.input;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BooleanSupplier;
import net.zodac.tracker.framework.ExecutionCancellation;
import net.zodac.tracker.framework.exception.CancelledInputException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Coordinates requests for a user input (such as solving a captcha in the browser of a manual tracker) between the execution, which waits for
 * the input, and the web UI, which shows each {@link PendingUserInput} and sends the user's {@link UserInputResponse}.
 *
 * <p>
 * A request is also completed without a response when the page in the browser changes (the user has progressed past the page themselves). A
 * request never times out: it waits until the user responds, or the execution is cancelled.
 *
 * <p>
 * The time each tracker has spent waiting for inputs is recorded (see {@link #waitedMillis(String)}), so it can be excluded from the tracker's
 * duration.
 */
public final class UserInputRequests {

    private static final Logger LOGGER = LogManager.getLogger();
    private static final long POLL_INTERVAL_MILLIS = 500L;

    private static final Lock LOCK = new ReentrantLock();
    private static final Condition RESPONDED = LOCK.newCondition();
    // Every registered request, and the response to each request the user has responded to (until its tracker sees it)
    private static final Map<Long, PendingUserInput> PENDING = new LinkedHashMap<>();
    private static final Map<Long, UserInputResponse> RESPONSES = new HashMap<>();
    // The total time spent waiting by the requests of each tracker that have finished, since the tracker was last reset
    private static final Map<String, Long> WAITED_MILLIS = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    private static long nextId = 1L;

    private UserInputRequests() {

    }

    /**
     * Waits for the user to provide an input. Returns once the user selects {@link UserInputResponse#CONTINUE}, or the page in the browser
     * changes.
     *
     * @param trackerName the name of the tracker waiting for the input
     * @param message     what the user needs to do
     * @param pageChanged returns {@code true} once the page in the browser has changed, checked periodically while waiting
     * @throws CancelledInputException thrown if the user selects {@link UserInputResponse#SKIP}
     * @throws net.zodac.tracker.framework.exception.ExecutionCancelledException thrown if the execution or the tracker is cancelled while waiting
     */
    public static void await(final String trackerName, final String message, final BooleanSupplier pageChanged) {
        final PendingUserInput request = register(trackerName, message);
        final long id = request.id();
        LOGGER.info("Waiting for user input: {}", message);

        try {
            waitForResponse(id, trackerName, pageChanged);
        } catch (final InterruptedException e) {
            // The waiting thread is only interrupted when the execution is cancelled
            Thread.currentThread().interrupt();
            ExecutionCancellation.throwIfCancelled();
            throw new IllegalStateException("Interrupted while waiting for user input", e);
        } finally {
            LOCK.lock();
            try {
                PENDING.remove(id);
                RESPONSES.remove(id);
                WAITED_MILLIS.merge(trackerName, Math.max(0L, System.currentTimeMillis() - request.requestedAt()), Long::sum);
            } finally {
                LOCK.unlock();
            }
        }
    }

    private static PendingUserInput register(final String trackerName, final String message) {
        LOCK.lock();
        try {
            final PendingUserInput request = new PendingUserInput(nextId, trackerName, message, System.currentTimeMillis());
            nextId++;
            PENDING.put(request.id(), request);
            return request;
        } finally {
            LOCK.unlock();
        }
    }

    private static void waitForResponse(final long id, final String trackerName, final BooleanSupplier pageChanged) throws InterruptedException {
        while (true) {
            // Also stops waiting once the tracker itself is cancelled
            ExecutionCancellation.throwIfCancelled(trackerName);

            LOCK.lock();
            try {
                if (!PENDING.containsKey(id)) {
                    throw new IllegalStateException("Request #%d is no longer registered".formatted(id));
                }

                final UserInputResponse response = RESPONSES.get(id);
                if (response != null) {
                    if (response == UserInputResponse.SKIP) {
                        LOGGER.trace("User skipped the input for request #{}", id);
                        throw new CancelledInputException();
                    }
                    LOGGER.trace("User provided the input for request #{}", id);
                    return;
                }

                final boolean signalled = RESPONDED.await(POLL_INTERVAL_MILLIS, TimeUnit.MILLISECONDS);
                LOGGER.trace("Woke waiting for request #{} (signalled: {})", id, signalled);
            } finally {
                LOCK.unlock();
            }

            if (pageChanged.getAsBoolean()) {
                LOGGER.trace("Page change detected for request #{}, continuing", id);
                return;
            }
        }
    }

    /**
     * Retrieves every request that is waiting for a user input, oldest first.
     *
     * @return the {@link PendingUserInput}s
     */
    public static List<PendingUserInput> pending() {
        LOCK.lock();
        try {
            return PENDING.values()
                .stream()
                .filter(request -> !RESPONSES.containsKey(request.id()))
                .toList();
        } finally {
            LOCK.unlock();
        }
    }

    /**
     * Retrieves the total time a tracker has spent waiting for user inputs since it was last reset (with {@link #resetWaited(String)}), counting
     * only the requests that have finished.
     *
     * @param trackerName the name of the tracker (case-insensitive)
     * @return the time spent waiting, in milliseconds
     */
    public static long waitedMillis(final String trackerName) {
        LOCK.lock();
        try {
            return WAITED_MILLIS.getOrDefault(trackerName, 0L);
        } finally {
            LOCK.unlock();
        }
    }

    /**
     * Resets the time a tracker has spent waiting for user inputs, when it starts executing.
     *
     * @param trackerName the name of the tracker (case-insensitive)
     */
    public static void resetWaited(final String trackerName) {
        LOCK.lock();
        try {
            WAITED_MILLIS.remove(trackerName);
        } finally {
            LOCK.unlock();
        }
    }

    /**
     * Provides the user's response to a waiting request.
     *
     * @param id       the {@link PendingUserInput#id()}
     * @param response the {@link UserInputResponse}
     * @return {@code true} if the request was waiting for a response, {@code false} if it has already finished
     */
    public static boolean respond(final long id, final UserInputResponse response) {
        LOCK.lock();
        try {
            if (!PENDING.containsKey(id) || RESPONSES.containsKey(id)) {
                return false;
            }
            RESPONSES.put(id, response);
            RESPONDED.signalAll();
            return true;
        } finally {
            LOCK.unlock();
        }
    }

    /**
     * A request for a user input that has not yet been answered, as shown in the web UI.
     *
     * @param id          the identifier of the request, used to respond to it
     * @param trackerName the name of the tracker waiting for the input
     * @param message     what the user needs to do
     * @param requestedAt when the input was requested, as epoch milliseconds
     */
    public record PendingUserInput(long id, String trackerName, String message, long requestedAt) {

    }
}
