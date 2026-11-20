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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import net.zodac.tracker.framework.ExecutionCancellation;
import net.zodac.tracker.framework.exception.CancelledInputException;
import net.zodac.tracker.framework.exception.ExecutionCancelledException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link UserInputRequests}.
 */
class UserInputRequestsTest {

    private static final String TRACKER_NAME = "Tracker";
    private static final String MESSAGE = "Solve the captcha";
    private static final long WAIT_SECONDS = 5L;

    @BeforeEach
    void setUp() {
        ExecutionCancellation.reset();
    }

    @AfterEach
    void tearDown() {
        ExecutionCancellation.reset();
    }

    @Test
    void givenWaitingRequest_whenContinued_thenRequestCompletes() throws Exception {
        final CompletableFuture<Void> waiting = awaitInBackground(() -> false);
        final UserInputRequests.PendingUserInput pendingInput = awaitPendingInput();

        assertThat(pendingInput)
            .returns(TRACKER_NAME, UserInputRequests.PendingUserInput::trackerName)
            .returns(MESSAGE, UserInputRequests.PendingUserInput::message);
        assertThat(UserInputRequests.respond(pendingInput.id(), UserInputResponse.CONTINUE))
            .isTrue();

        waiting.get(WAIT_SECONDS, TimeUnit.SECONDS);
        assertThat(UserInputRequests.pending())
            .isEmpty();
    }

    @Test
    void givenWaitingRequest_whenSkipped_thenCancelledInputExceptionIsThrown() throws Exception {
        final CompletableFuture<Void> waiting = awaitInBackground(() -> false);
        UserInputRequests.respond(awaitPendingInput().id(), UserInputResponse.SKIP);

        assertThatThrownBy(() -> waiting.get(WAIT_SECONDS, TimeUnit.SECONDS))
            .hasCauseInstanceOf(CancelledInputException.class);
    }

    @Test
    void givenWaitingRequest_whenPageChanges_thenRequestCompletes() throws Exception {
        final AtomicBoolean pageChanged = new AtomicBoolean(false);
        final CompletableFuture<Void> waiting = awaitInBackground(pageChanged::get);
        awaitPendingInput();

        pageChanged.set(true);

        waiting.get(WAIT_SECONDS, TimeUnit.SECONDS);
        assertThat(waiting)
            .isCompleted();
    }

    @Test
    void givenWaitingRequest_whenExecutionCancelled_thenExecutionCancelledExceptionIsThrown() throws Exception {
        final CompletableFuture<Void> waiting = awaitInBackground(() -> false);
        awaitPendingInput();

        ExecutionCancellation.requestCancellation();

        assertThatThrownBy(() -> waiting.get(WAIT_SECONDS, TimeUnit.SECONDS))
            .hasCauseInstanceOf(ExecutionCancelledException.class);
    }

    @Test
    void givenNoWaitingRequest_whenResponded_thenResponseIsRejected() {
        assertThat(UserInputRequests.respond(Long.MAX_VALUE, UserInputResponse.CONTINUE))
            .isFalse();
    }

    private static CompletableFuture<Void> awaitInBackground(final BooleanSupplier pageChanged) {
        return CompletableFuture.runAsync(() -> UserInputRequests.await(TRACKER_NAME, MESSAGE, pageChanged));
    }

    private static UserInputRequests.PendingUserInput awaitPendingInput() throws InterruptedException {
        final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(WAIT_SECONDS);
        while (System.nanoTime() < deadline) {
            final List<UserInputRequests.PendingUserInput> pending = UserInputRequests.pending();
            if (!pending.isEmpty()) {
                return pending.getFirst();
            }
            Thread.sleep(10L);
        }
        throw new AssertionError("No request for a user input was made");
    }
}
