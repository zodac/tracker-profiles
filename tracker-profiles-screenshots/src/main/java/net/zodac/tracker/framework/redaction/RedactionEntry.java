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

package net.zodac.tracker.framework.redaction;

import java.util.function.Predicate;
import net.zodac.tracker.framework.redactor.Redactor;
import net.zodac.tracker.handler.redaction.Redaction;
import org.openqa.selenium.remote.RemoteWebDriver;

/**
 * A single entry in the {@link RedactionRegistry}, binding a {@link Redaction} sub-interface to its redact and empty-check operations.
 *
 * @param redactionInterface the {@link Redaction} sub-interface this entry dispatches for
 * @param invoker            the method reference that performs the redaction
 * @param emptyCheck         the predicate that returns {@code true} when the handler has no selectors for this category
 * @param description        the human-readable name of this redaction category, used for logging
 * @param <T>                the {@link Redaction} sub-interface type
 */
record RedactionEntry<T extends Redaction>(Class<T> redactionInterface, Invoker<T> invoker, Predicate<T> emptyCheck, String description) {

    /**
     * Static factory for a {@link RedactionEntry}.
     *
     * @param redactionInterface the {@link Redaction} sub-interface this entry dispatches for
     * @param invoker            the method reference that performs the redaction
     * @param emptyCheck         the predicate that returns {@code true} when the handler has no selectors for this category
     * @param description        the human-readable name of this redaction category, used for logging
     * @param <T>                the {@link Redaction} sub-interface type
     * @return the created {@link RedactionEntry}
     */
    static <T extends Redaction> RedactionEntry<T> of(final Class<T> redactionInterface, final Invoker<T> invoker,
                                                      final Predicate<T> emptyCheck, final String description) {
        return new RedactionEntry<>(redactionInterface, invoker, emptyCheck, description);
    }

    /**
     * Whether this entry's {@link Redaction} sub-interface is implemented by the given {@code handler}.
     *
     * @param handler the handler to inspect
     * @return {@code true} if the handler implements this entry's redaction interface
     */
    boolean applies(final Object handler) {
        return redactionInterface.isInstance(handler);
    }

    /**
     * Invokes the redaction against {@code handler}. Caller must have confirmed {@link #applies(Object)} first.
     *
     * @param handler  the handler to redact against
     * @param driver   the {@link RemoteWebDriver} the page is loaded in
     * @param redactor the {@link Redactor} used to mutate the matched elements
     * @return the number of elements redacted by this category
     */
    int invoke(final Object handler, final RemoteWebDriver driver, final Redactor redactor) {
        return invoker.invoke(redactionInterface.cast(handler), driver, redactor);
    }

    /**
     * Whether the {@code handler} has no selectors for this redaction category. Caller must have confirmed {@link #applies(Object)} first.
     *
     * @param handler the handler to inspect
     * @return {@code true} if the handler's selectors for this category are empty
     */
    boolean isEmpty(final Object handler) {
        return emptyCheck.test(redactionInterface.cast(handler));
    }
}
