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
import static net.zodac.tracker.framework.xpath.HtmlElement.tbody;
import static net.zodac.tracker.framework.xpath.HtmlElement.td;
import static net.zodac.tracker.framework.xpath.HtmlElement.tr;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.atIndex;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.containsHref;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.withClass;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.withId;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.withType;

import java.util.Collection;
import java.util.List;
import net.zodac.tracker.framework.annotation.TrackerHandler;
import net.zodac.tracker.framework.xpath.XpathBuilder;
import net.zodac.tracker.handler.redaction.RedactsIpAddress;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

/**
 * Implementation of {@link AbstractTrackerHandler} for the {@code Alexandria} tracker.
 */
@TrackerHandler(name = "Alexandria", url = "https://alxdria.org/")
public class Alexandria extends AbstractTrackerHandler implements RedactsIpAddress {

    @Override
    public By loginPageSelector() {
        return XpathBuilder
            .from(a, containsHref("/login"))
            .build();
    }

    @Override
    protected By loginButtonSelector() {
        return XpathBuilder
            .from(button, withType("submit"))
            .build();
    }

    @Override
    protected By profileLinkSelector() {
        return XpathBuilder
            .from(div, withId("desktopNavMenu"))
            .descendant(a, containsHref("/user/"))
            .build();
    }

    @Override
    protected By profilePageElementSelector() {
        return By.id("user-view");
    }

    @Override
    public Collection<By> ipAddressElements() {
        return List.of(
            XpathBuilder
                .from(tbody, withClass("p-datatable-tbody"))
                .child(tr, atIndex(1))
                .child(td, atIndex(3))
                .build()
        );
    }

    @Override
    protected By logoutButtonSelector() {
        openUserDropdownMenu();
        return XpathBuilder
            .from(div, withClass("sign-out"))
            .build();
    }

    private void openUserDropdownMenu() {
        LOGGER.debug("\t\t- Hovering over user icon to make logout button interactable");
        final By userLinkIcon = XpathBuilder
            .from(div, withId("desktopNavMenu"))
            .descendant(a, containsHref("/user/"))
            .build();
        final WebElement piUserIcon = driver().findElement(userLinkIcon);
        browserInteractionHelper().moveTo(piUserIcon);
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * For {@link Alexandria}, we are redirected to the login page, not the tracker home page.
     */
    @Override
    protected By postLogoutElementSelector() {
        return usernameFieldSelector();
    }
}
