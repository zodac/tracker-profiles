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

package net.zodac.tracker.handler.definition;

import net.zodac.tracker.redaction.Redactor;
import org.openqa.selenium.remote.RemoteWebDriver;

/**
 * Functional interface used by {@link RedactionEntry} to invoke a {@link Redaction} sub-interface's redact method via a typed method reference,
 * avoiding the need for reflective dispatch.
 *
 * @param <T> the {@link Redaction} sub-interface type
 */
@FunctionalInterface
interface Invoker<T extends Redaction> {

    /**
     * Invokes the redact method of {@code T} against {@code target}.
     *
     * @param target   the handler instance, already cast to {@code T}
     * @param driver   the {@link RemoteWebDriver} the page is loaded in
     * @param redactor the {@link Redactor} used to mutate the matched elements
     * @return the number of elements redacted
     */
    int invoke(T target, RemoteWebDriver driver, Redactor redactor);
}
