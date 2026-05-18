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
import org.openqa.selenium.WebElement;
import org.openqa.selenium.remote.RemoteWebDriver;

/**
 * Marker interface for redaction capabilities that an {@link net.zodac.tracker.handler.AbstractTrackerHandler} may opt into. Each implementation
 * declares one category of sensitive {@link WebElement}s (email, IP address, passkey, etc.) found on the user profile page that must be redacted
 * before the screenshot is taken.
 *
 * <p>
 * Implementations must follow this shape to be picked up by {@link RedactionRegistry}:
 * <ol>
 *     <li>A single abstract no-arg method returning either {@link java.util.Collection} or {@link java.util.Map} of selectors.</li>
 *     <li>A default method with signature {@code int redactXxx(RemoteWebDriver, Redactor)} that performs the redaction and returns the number of
 *     redacted {@link WebElement}s.</li>
 * </ol>
 *
 * <p>
 * Adding a new redaction category is purely additive: create a new interface in this package extending {@link Redaction} and the registry will
 * dispatch to it without any further wiring.
 *
 * @see RedactionRegistry
 * @see Redactor
 * @see RemoteWebDriver
 */
public interface Redaction {

}
