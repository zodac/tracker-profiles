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
import static net.zodac.tracker.framework.xpath.HtmlElement.button;
import static net.zodac.tracker.framework.xpath.HtmlElement.div;
import static net.zodac.tracker.framework.xpath.HtmlElement.li;
import static net.zodac.tracker.framework.xpath.HtmlElement.main;
import static net.zodac.tracker.framework.xpath.HtmlElement.table;
import static net.zodac.tracker.framework.xpath.HtmlElement.tbody;
import static net.zodac.tracker.framework.xpath.HtmlElement.td;
import static net.zodac.tracker.framework.xpath.HtmlElement.tr;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.atIndex;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.atLastIndex;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.withClass;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.withType;

import java.util.Collection;
import java.util.List;
import net.zodac.tracker.framework.annotation.TrackerHandler;
import net.zodac.tracker.framework.xpath.XpathBuilder;
import org.openqa.selenium.By;

/**
 * Extension of the {@link Unit3dHandler} for the {@code Aura4K} tracker.
 */
@TrackerHandler(name = "Aura4K", url = "https://aura4k.net/")
public class Aura4k extends Unit3dHandler {

    @Override
    protected By loginButtonSelector() {
        return XpathBuilder
            .from(button, withType("submit"))
            .build();
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * For {@link Aura4k}, the welcome banner doesn't use the normal HTML element as other {@link Unit3dHandler}s, so we wait for it to disappear. But
     * we only need it for the profile page, not logout.
     */
    @Override
    protected By profileLinkSelector() {
        LOGGER.debug("\t\t- Waiting for welcome pop-up to disappear");
        final By loginPopupSelector = XpathBuilder
            .from(body, atIndex(1))
            .child(div, atLastIndex())
            .build();
        // The pop-up can be slow to load, so first wait for it to appear, then wait for it to disappear
        browserInteractionHelper.waitForElementToBeVisible(loginPopupSelector, pageLoadDuration());
        browserInteractionHelper.waitForElementToDisappear(loginPopupSelector, pageLoadDuration());

        super.openUserDropdownMenu();
        return XpathBuilder
            .from(li, withClass("top-nav__dropdown"))
            .descendant(a, withClass("top-nav__username"))
            .build();
    }

    @Override
    protected By profilePageElementSelector() {
        return XpathBuilder
            .from(main, withClass("profile-page-wow"))
            .build();
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * For {@link Aura4k}, the email address is not visible on the profile page.
     */
    @Override
    public Collection<By> emailElements() {
        return List.of();
    }

    @Override
    public Collection<By> ipAddressElements() {
        return List.of(
            XpathBuilder
                .from(table, withClass("wow-table"))
                .child(tbody)
                .child(tr)
                .child(td, atIndex(2))
                .build()
        );
    }
}
