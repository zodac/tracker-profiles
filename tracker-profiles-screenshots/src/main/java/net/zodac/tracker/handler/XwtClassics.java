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
import static net.zodac.tracker.framework.xpath.HtmlElement.body;
import static net.zodac.tracker.framework.xpath.HtmlElement.div;
import static net.zodac.tracker.framework.xpath.HtmlElement.input;
import static net.zodac.tracker.framework.xpath.HtmlElement.table;
import static net.zodac.tracker.framework.xpath.HtmlElement.tbody;
import static net.zodac.tracker.framework.xpath.HtmlElement.td;
import static net.zodac.tracker.framework.xpath.HtmlElement.tr;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.atIndex;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.withClass;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.withType;

import net.zodac.tracker.framework.annotation.TrackerHandler;
import net.zodac.tracker.framework.xpath.NamedHtmlElement;
import net.zodac.tracker.framework.xpath.XpathBuilder;
import org.jspecify.annotations.Nullable;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

/**
 * Implementation of {@link AbstractTrackerHandler} for the {@code XWT-Classics} tracker.
 */
@TrackerHandler(name = "XWT-Classics", url = "https://xwt-classics.net/")
public class XwtClassics extends AbstractTrackerHandler {

    @Override
    protected By loginButtonSelector() {
        return XpathBuilder
            .from(input, withClass("pg-btn"), withType("submit"))
            .build();
    }

    @Override
    protected By profileLinkSelector() {
        return XpathBuilder
            .from(NamedHtmlElement.of("h1"), withClass("hero-title"))
            .build();
    }

    @Override
    @Nullable
    protected By profilePageElementSelector() {
        return null;
    }

    @Override
    protected By logoutButtonSelector() {
        // Must open the user panel to show the logout button
        final By userPanelSelector = XpathBuilder
            .from(body, atIndex(1))
            .child(table, atIndex(2))
            .child(tbody, atIndex(1))
            .child(tr, atIndex(1))
            .child(td, atIndex(4))
            .child(a, atIndex(1))
            .build();
        final WebElement userPanel = driver().findElement(userPanelSelector);
        clickButton(userPanel);

        final By logoutButton = XpathBuilder
            .from(div, withClass("profile-wrap"))
            .child(div, atIndex(1))
            .child(div, atIndex(2))
            .child(div, atIndex(2))
            .child(a, atIndex(1))
            .build();
        browserInteractionHelper().waitForElementToBeInteractable(logoutButton, pageLoadDuration());
        return logoutButton;
    }
}
