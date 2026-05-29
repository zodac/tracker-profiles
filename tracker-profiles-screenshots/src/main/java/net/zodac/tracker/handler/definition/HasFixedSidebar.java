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

import static net.zodac.tracker.framework.xpath.HtmlElement.div;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.atIndex;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.withClass;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.withId;

import net.zodac.tracker.framework.xpath.NamedHtmlElement;
import net.zodac.tracker.framework.xpath.XpathBuilder;
import net.zodac.tracker.util.BrowserInteractionHelper;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.remote.RemoteWebDriver;

/**
 * Marks an {@link net.zodac.tracker.handler.AbstractTrackerHandler} as having a fixed side on the user profile page that must be unfixed before
 * taking the screenshot. This prevents the sidebar from appearing multiple times as the page is scrolled during the screenshot.
 */
public interface HasFixedSidebar {

    /**
     * Finds the fixed sidebar element on the tracker's user profile page and updates it to not be fixed.
     *
     * @param driver the {@link RemoteWebDriver}
     */
    default void unfixSidebar(final RemoteWebDriver driver) {
        final BrowserInteractionHelper browserInteractionHelper = BrowserInteractionHelper.of(driver);
        final By sidebarSelector = By.id("hoe-left-panel");
        final WebElement sidebarElement = driver.findElement(sidebarSelector);
        browserInteractionHelper.makeUnfixed(sidebarElement);

        // Unfixing the sidebar causes the main user details <div> to expand
        // We remove the wrapping <div> and keep the internal content instead to shrink this back to 'normal' size
        final By expandedDivSelector = XpathBuilder
            .from(NamedHtmlElement.of("section"), withId("main-content"))
            .child(div, atIndex(1))
            .child(div, atIndex(1))
            .build();
        final WebElement expandedDivElement = driver.findElement(expandedDivSelector);
        browserInteractionHelper.unwrapElement(expandedDivElement);

        // Finally, we unfix the left header which contains the logo
        final By logoSelector = XpathBuilder
            .from(div, withClass("hoe-left-header"))
            .build();
        final WebElement logoElement = driver.findElement(logoSelector);
        browserInteractionHelper.makeUnfixed(logoElement);
    }
}
