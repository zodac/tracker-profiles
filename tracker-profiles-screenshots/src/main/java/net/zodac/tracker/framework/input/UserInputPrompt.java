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

/**
 * Shows a request for a user input outside the web UI (such as a dialog in front of the browser of a manual tracker), for as long as the request
 * is waiting. The prompt responds with {@link UserInputRequests#respond(long, UserInputResponse)}, the same as the web UI, so whichever the user
 * answers first completes the request.
 */
@FunctionalInterface
public interface UserInputPrompt {

    /**
     * A {@link UserInputPrompt} that shows nothing, so the request is only shown in the web UI.
     */
    UserInputPrompt NONE = _ -> () -> {
    };

    /**
     * Shows the prompt for a request that is now waiting for a user input.
     *
     * @param requestId the {@link UserInputRequests.PendingUserInput#id()} of the request
     * @return closes the prompt, run once the request is no longer waiting (for any reason)
     */
    Runnable show(long requestId);
}
