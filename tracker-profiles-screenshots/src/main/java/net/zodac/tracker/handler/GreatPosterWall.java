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
import static net.zodac.tracker.framework.xpath.HtmlElement.footer;
import static net.zodac.tracker.framework.xpath.HtmlElement.li;
import static net.zodac.tracker.framework.xpath.HtmlElement.p;
import static net.zodac.tracker.framework.xpath.HtmlElement.span;
import static net.zodac.tracker.framework.xpath.HtmlElement.ul;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.atIndex;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.withClass;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.withName;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.withType;

import java.util.Collection;
import java.util.List;
import net.zodac.tracker.framework.annotation.TrackerHandler;
import net.zodac.tracker.framework.xpath.XpathBuilder;
import net.zodac.tracker.handler.redaction.RedactsEmail;
import net.zodac.tracker.handler.redaction.RedactsIpAddress;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

/**
 * Implementation of {@link AbstractTrackerHandler} for the {@code GreatPosterWall} tracker.
 */
@TrackerHandler(name = "GreatPosterWall", url = "https://greatposterwall.com/")
public class GreatPosterWall extends AbstractTrackerHandler implements RedactsEmail, RedactsIpAddress {

    @Override
    public By loginPageSelector() {
        return By.id("login-a");
    }

    @Override
    protected By loginButtonSelector() {
        return XpathBuilder
            .from(button, withName("login"), withType("submit"))
            .build();
    }

    @Override
    protected By profileLinkSelector() {
        return By.id("header-username-value");
    }

    @Override
    protected By profilePageElementSelector() {
        return By.id("community_stats");
    }

    @Override
    public Collection<By> emailElements() {
        return List.of(
            XpathBuilder
                .from(div, withClass("LayoutMainSidebar-sidebar"))
                .child(div, atIndex(2))
                .child(ul, atIndex(1))
                .child(li, atIndex(3))
                .build()
        );
    }

    @Override
    public Collection<By> ipAddressElements() {
        return List.of(
            // IP address in profile sidebar
            XpathBuilder
                .from(div, withClass("LayoutMainSidebar-sidebar"))
                .child(div, atIndex(2))
                .child(ul, atIndex(1))
                .child(li, atIndex(4))
                .build(),
            // Last connected IP address
            XpathBuilder
                .from(footer, atIndex(1))
                .child(p, atIndex(2))
                .child(a, atIndex(1))
                .child(span, atIndex(3))
                .build()
        );
    }

    @Override
    protected By logoutButtonSelector() {
        openUserDropdownMenu();
        return XpathBuilder
            .from(a, withClass("is-logout"))
            .build();
    }

    private void openUserDropdownMenu() {
        LOGGER.debug("\t\t- Clicking user dropdown menu to make profile/logout button interactable");
        final By dropDownMenuSelector = XpathBuilder
            .from(span, withClass("HeaderProfile-avatarContainer"))
            .build();
        final WebElement dropDownMenu = driver.findElement(dropDownMenuSelector);
        clickButton(dropDownMenu);
    }
}
