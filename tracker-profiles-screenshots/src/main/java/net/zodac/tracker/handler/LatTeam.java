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

package net.zodac.tracker.handler;

import static net.zodac.tracker.framework.xpath.HtmlElement.a;
import static net.zodac.tracker.framework.xpath.HtmlElement.button;
import static net.zodac.tracker.framework.xpath.HtmlElement.li;
import static net.zodac.tracker.framework.xpath.HtmlElement.nav;
import static net.zodac.tracker.framework.xpath.HtmlElement.span;
import static net.zodac.tracker.framework.xpath.HtmlElement.ul;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.atIndex;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.withClass;

import net.zodac.tracker.framework.annotation.TrackerHandler;
import net.zodac.tracker.framework.xpath.XpathBuilder;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

/**
 * Extension of the {@link Unit3dHandler} for the {@code Lat-Team} tracker.
 */
@TrackerHandler(name = "Lat-Team", url = "https://lat-team.com/")
public class LatTeam extends Unit3dHandler {

    @Override
    protected By profileLinkSelector() {
        openUserDropdownMenu();
        return XpathBuilder
            .from(nav, withClass("user-sidebar__nav"))
            .child(ul, atIndex(1))
            .child(li, atIndex(1))
            .child(a, atIndex(1))
            .build();
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * For {@link LatTeam}, unlike most other {@code UNIT3D}-based trackers, the user profile dropdown does not open when the mouse hovers
     * over it. Instead, we need to click it.
     */
    @Override
    protected By logoutButtonSelector() {
        openUserDropdownMenu();
        return XpathBuilder
            .from(button, withClass("user-sidebar__menu-link--logout"))
            .build();
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * For {@link LatTeam}, there is a different XPath query needed to open the user dropdown menu than other UNIT3D-based trackers.
     */
    @Override
    protected void openUserDropdownMenu() {
        LOGGER.debug("\t\t- Clicking user dropdown menu to make profile/logout button interactable");
        final By logoutParentSelector = XpathBuilder
            .from(span, withClass("user-sidebar__toggle-label"))
            .build();
        final WebElement logoutParent = driver().findElement(logoutParentSelector);
        clickButton(logoutParent);
    }
}
