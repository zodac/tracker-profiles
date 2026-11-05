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
import static net.zodac.tracker.framework.xpath.HtmlElement.div;
import static net.zodac.tracker.framework.xpath.HtmlElement.header;
import static net.zodac.tracker.framework.xpath.HtmlElement.main;
import static net.zodac.tracker.framework.xpath.HtmlElement.nav;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.atIndex;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.withHref;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.withId;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.withType;

import java.util.List;
import net.zodac.tracker.framework.annotation.TrackerHandler;
import net.zodac.tracker.framework.xpath.NamedHtmlElement;
import net.zodac.tracker.framework.xpath.XpathBuilder;
import net.zodac.tracker.handler.definition.HasFixedHeader;
import net.zodac.tracker.handler.definition.HasFixedSidebar;
import net.zodac.tracker.util.BrowserInteractionHelper;
import org.jspecify.annotations.Nullable;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.remote.RemoteWebDriver;

/**
 * Implementation of {@link AbstractTrackerHandler} for the {@code Tr4ker} tracker.
 */
@TrackerHandler(name = "Tr4ker", url = "https://tr4ker.net/")
public class Tr4ker extends AbstractTrackerHandler implements HasFixedHeader, HasFixedSidebar {

    @Override
    @Nullable
    protected By loginPageSelector() {
        return XpathBuilder
            .from(a, withHref("/login"))
            .build();
    }

    @Override
    protected By usernameFieldSelector() {
        return By.name("identifier");
    }

    @Override
    protected By loginButtonSelector() {
        return XpathBuilder
            .from(button, withType("submit"))
            .build();
    }

    @Override
    protected By postLoginSelector() {
        return XpathBuilder
            .from(div, withId("root"))
            .child(div, atIndex(1))
            .child(nav, atIndex(1))
            .child(button, atIndex(1))
            .build();
    }

    @Override
    protected By profileLinkSelector() {
        final WebElement sidebarElement = driver().findElement(postLoginSelector());
        clickButton(sidebarElement);
        return XpathBuilder
            .from(a, withHref("/mon-compte/profil"))
            .build();
    }

    @Override
    protected By profilePageElementSelector() {
        return XpathBuilder
            .from(div, withId("root"))
            .child(div, atIndex(1))
            .child(div, atIndex(2))
            .child(main, atIndex(1))
            .child(div, atIndex(1))
            .child(div, atIndex(1))
            .child(div, atIndex(2))
            .build();
    }

    @Override
    public List<By> headerSelectors() {
        return List.of(
            XpathBuilder
                .from(header, atIndex(1))
                .build()
        );
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * For {@link Tr4ker}, the sidebar is collapsed by clicking a toggle button. The collapsed state persists across page reloads (stored as the
     * {@code aside-minimize} CSS class on the {@code <body>} element). This way the toggle is only clicked if the sidebar is not already collapsed,
     * to avoid re-opening it on a second attempt or a page refresh.
     *
     * <p>
     * Even when collapsed, the sidebar element retains {@code position: fixed} via the {@code .aside-fixed .aside} CSS rule applied through
     * the {@code <body>} class. So after collapsing, the sidebar element itself is also explicitly unfixed.
     */
    @Override
    public void unfixSidebar(final RemoteWebDriver driver) {
        final By toggleSelector = XpathBuilder
            .from(div, withId("root"))
            .child(div, atIndex(1))
            .child(NamedHtmlElement.of("aside"), atIndex(1))
            .child(div, atIndex(1))
            .child(button, atIndex(1))
            .build();
        final WebElement toggle = driver.findElement(toggleSelector);
        clickButton(toggle);

        final BrowserInteractionHelper helper = BrowserInteractionHelper.of(driver);
        final By sidebarSelector = XpathBuilder
            .from(div, withId("root"))
            .child(div, atIndex(1))
            .child(nav, atIndex(1))
            .build();
        final WebElement sidebar = driver.findElement(sidebarSelector);
        helper.makeUnfixed(sidebar);
    }

    @Override
    protected By logoutButtonSelector() {
        reloadProfilePage(); // To bring back the sidebar we just hid
        return XpathBuilder
            .from(div, withId("root"))
            .child(div, atIndex(1))
            .child(nav, atIndex(1))
            .child(button, atIndex(8))
            .build();
    }
}
