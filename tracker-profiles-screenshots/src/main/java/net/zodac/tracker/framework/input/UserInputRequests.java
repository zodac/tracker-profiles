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

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BooleanSupplier;
import net.zodac.tracker.framework.ExecutionCancellation;
import net.zodac.tracker.framework.config.Configuration;
import net.zodac.tracker.framework.exception.CancelledInputException;
import net.zodac.tracker.framework.exception.NoUserInputException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jspecify.annotations.Nullable;

/**
 * Coordinates requests for a user input (such as solving a captcha in the browser of a manual tracker) between the execution, which waits for
 * the input, and the web UI, which shows each {@link PendingUserInput} and sends the user's {@link UserInputResponse}.
 *
 * <p>
 * A request is also completed without a response when the page in the browser changes (the user has progressed past the page themselves), and
 * fails if {@link net.zodac.tracker.framework.config.ApplicationConfiguration#inputTimeoutDuration()} passes first.
 */
public final class UserInputRequests {

    private static final Logger LOGGER = LogManager.getLogger();
    private static final long POLL_INTERVAL_MILLIS = 500L;

    private static final Lock LOCK = new ReentrantLock();
    private static final Condition RESPONDED = LOCK.newCondition();
    private static final Map<Long, Request> PENDING = new LinkedHashMap<>();
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
     * @throws NoUserInputException    thrown if no input is received before the timeout, or the waiting thread is interrupted
     * @throws net.zodac.tracker.framework.exception.ExecutionCancelledException thrown if the execution is cancelled while waiting
     */
    public static void await(final String trackerName, final String message, final BooleanSupplier pageChanged) {
        final long id = register(trackerName, message);
        LOGGER.info("Waiting for user input: {}", message);

        try {
            waitForResponse(id, pageChanged);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new NoUserInputException(Configuration.get().inputTimeoutDuration(), e);
        } finally {
            LOCK.lock();
            try {
                PENDING.remove(id);
            } finally {
                LOCK.unlock();
            }
        }
    }

    private static long register(final String trackerName, final String message) {
        LOCK.lock();
        try {
            final long id = nextId;
            nextId++;
            PENDING.put(id, new Request(id, trackerName, message, System.currentTimeMillis(), Configuration.get().inputTimeoutDuration()));
            return id;
        } finally {
            LOCK.unlock();
        }
    }

    private static void waitForResponse(final long id, final BooleanSupplier pageChanged) throws InterruptedException {
        while (true) {
            ExecutionCancellation.throwIfCancelled();

            LOCK.lock();
            try {
                final Request request = PENDING.get(id);
                if (request == null) {
                    throw new IllegalStateException("Request #%d is no longer registered".formatted(id));
                }

                if (request.response != null) {
                    if (request.response == UserInputResponse.SKIP) {
                        LOGGER.trace("User skipped the input for request #{}", id);
                        throw new CancelledInputException();
                    }
                    LOGGER.trace("User provided the input for request #{}", id);
                    return;
                }

                final Long expiresAt = request.expiresAt();
                if (expiresAt != null && System.currentTimeMillis() >= expiresAt) {
                    LOGGER.trace("Request #{} timed out with no input", id);
                    throw new NoUserInputException(Duration.ofMillis(expiresAt - request.requestedAt), new IllegalStateException("Timed out"));
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
            final List<PendingUserInput> pending = new ArrayList<>();
            for (final Request request : PENDING.values()) {
                if (request.response == null) {
                    pending.add(request.toView());
                }
            }
            return pending;
        } finally {
            LOCK.unlock();
        }
    }

    /**
     * Retrieves a request if it is still waiting for a user input.
     *
     * @param id the {@link PendingUserInput#id()}
     * @return the {@link PendingUserInput}, or {@link Optional#empty()} if the request has finished
     */
    public static Optional<PendingUserInput> find(final long id) {
        LOCK.lock();
        try {
            final Request request = PENDING.get(id);
            if (request == null || request.response != null) {
                return Optional.empty();
            }
            return Optional.of(request.toView());
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
            final Request request = PENDING.get(id);
            if (request == null || request.response != null) {
                return false;
            }
            request.response = response;
            RESPONDED.signalAll();
            return true;
        } finally {
            LOCK.unlock();
        }
    }

    /**
     * Gives the user more time to provide the input for a waiting request. Has no effect if user inputs do not time out.
     *
     * @param id        the {@link PendingUserInput#id()}
     * @param extension how much longer to wait
     * @return {@code true} if the request was waiting for a response, {@code false} if it has already finished
     */
    public static boolean extend(final long id, final Duration extension) {
        LOCK.lock();
        try {
            final Request request = PENDING.get(id);
            if (request == null || request.response != null) {
                return false;
            }
            request.extensionMillis += extension.toMillis();
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
     * @param expiresAt   when the request times out, as epoch milliseconds, or {@code null} if it does not time out
     */
    public record PendingUserInput(long id, String trackerName, String message, long requestedAt, @Nullable Long expiresAt) {

    }

    // Mutable state of a request, only accessed while holding LOCK
    private static final class Request {

        private final long id;
        private final String trackerName;
        private final String message;
        private final long requestedAt;
        private final Duration timeout;
        private long extensionMillis;
        private @Nullable UserInputResponse response;

        private Request(final long id, final String trackerName, final String message, final long requestedAt, final Duration timeout) {
            this.id = id;
            this.trackerName = trackerName;
            this.message = message;
            this.requestedAt = requestedAt;
            this.timeout = timeout;
        }

        // No expiry when the timeout is disabled (zero)
        private @Nullable Long expiresAt() {
            if (timeout.isZero()) {
                return null;
            }
            return requestedAt + timeout.toMillis() + extensionMillis;
        }

        private PendingUserInput toView() {
            return new PendingUserInput(id, trackerName, message, requestedAt, expiresAt());
        }
    }
}
