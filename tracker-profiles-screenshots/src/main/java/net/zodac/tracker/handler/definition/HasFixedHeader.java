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

import java.util.List;
import net.zodac.tracker.util.BrowserInteractionHelper;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.remote.RemoteWebDriver;

/**
 * Marks an {@link net.zodac.tracker.handler.AbstractTrackerHandler} as having one or many fixed header elements on the user profile page that must be
 * unfixed before taking the screenshot. This prevents the header from appearing multiple times as the page is scrolled during the screenshot.
 */
@FunctionalInterface
public interface HasFixedHeader {

    /**
     * Finds the fixed header elements on the tracker's user profile page and updates them to not be fixed.
     *
     * @param driver         the {@link RemoteWebDriver}
     * @param headerSelectors the header {@link By} selectors to unfix
     * @see BrowserInteractionHelper#makeUnfixed(WebElement)
     */
    default void unfixHeaders(final RemoteWebDriver driver, final List<By> headerSelectors) {
        final BrowserInteractionHelper browserInteractionHelper = BrowserInteractionHelper.of(driver);
        for (final By headerSelector : headerSelectors) {
            final WebElement headerElement = driver.findElement(headerSelector);
            browserInteractionHelper.makeUnfixed(headerElement);
        }
    }

    /**
     * The {@link By} selectors to find the header {@link WebElement}.
     *
     * @return the header {@link By} selectors
     */
    List<By> headerSelectors();
}
