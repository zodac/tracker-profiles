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
 * Marks an {@link net.zodac.tracker.handler.AbstractTrackerHandler} as having a dynamic button that can jump the page to the top or to the bottom.
 */
@FunctionalInterface
public interface HasJumpButtons {

    /**
     * Finds the top/bottom jump buttons on the tracker's user profile page and hides them.
     *
     * @param driver              the {@link RemoteWebDriver}
     * @param jumpButtonSelectors the jump button {@link By} selectors to hide
     * @see BrowserInteractionHelper#removeElement(WebElement)
     */
    default void hideJumpButtons(final RemoteWebDriver driver, final List<By> jumpButtonSelectors) {
        final BrowserInteractionHelper browserInteractionHelper = BrowserInteractionHelper.of(driver);
        for (final By jumpButtonSelector : jumpButtonSelectors) {
            final WebElement jumpButton = driver.findElement(jumpButtonSelector);
            browserInteractionHelper.removeElement(jumpButton);
        }
    }

    /**
     * The {@link List} of {@link By} selectors to find the top/bottom jump button {@link WebElement}s.
     *
     * @return the jump button {@link By} selectors
     */
    List<By> jumpButtonSelectors();
}
