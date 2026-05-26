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
import static net.zodac.tracker.framework.xpath.HtmlElement.li;
import static net.zodac.tracker.framework.xpath.HtmlElement.nav;
import static net.zodac.tracker.framework.xpath.HtmlElement.span;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.atIndex;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.withAttribute;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.withClass;

import java.util.Collection;
import java.util.List;
import net.zodac.tracker.framework.TrackerType;
import net.zodac.tracker.framework.annotation.TrackerHandler;
import net.zodac.tracker.framework.xpath.XpathBuilder;
import net.zodac.tracker.handler.definition.HasCloudflareCheck;
import net.zodac.tracker.handler.definition.HasFixedHeader;
import net.zodac.tracker.handler.definition.HasFixedSidebar;
import net.zodac.tracker.handler.redaction.RedactsEmail;
import net.zodac.tracker.handler.redaction.RedactsIpAddress;
import net.zodac.tracker.handler.redaction.RedactsTorrentPasskey;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.remote.RemoteWebDriver;

/**
 * Implementation of {@link AbstractTrackerHandler} for the {@code ABTorrents} tracker.
 */
@TrackerHandler(name = "LastFiles", type = TrackerType.MANUAL, url = "https://last-torrents.org/")
public class LastFiles extends AbstractTrackerHandler
    implements HasCloudflareCheck, HasFixedHeader, HasFixedSidebar, RedactsEmail, RedactsIpAddress, RedactsTorrentPasskey {

    @Override
    protected By usernameFieldSelector() {
        return By.name("name");
    }

    @Override
    protected By loginButtonSelector() {
        return XpathBuilder
            .from(button, withClass("btn-elite"))
            .build();
    }

    @Override
    protected By postLoginSelector() {
        return XpathBuilder
            .from(a, withClass("nav-link"))
            .build();
    }

    @Override
    protected By profileLinkSelector() {
        openUserDropdownMenu();
        return XpathBuilder
            .from(li, withClass("user-footer"))
            .child(a, atIndex(1))
            .build();
    }

    @Override
    protected By profilePageElementSelector() {
        return XpathBuilder
            .from(span, withClass("username-text"))
            .build();
    }

    @Override
    public List<By> headerSelectors() {
        return List.of(
            XpathBuilder
                .from(nav, withClass("navbar"))
                .build()
        );
    }

    @Override
    public void unfixSidebar(final RemoteWebDriver driver) {
        final By toggleSelector = XpathBuilder
            .from(a, withAttribute("data-lte-toggle", "sidebar"), withClass("nav-link"))
            .build();
        final WebElement toggle = driver.findElement(toggleSelector);
        clickButton(toggle);
    }

    @Override
    public Collection<By> emailElements() {
        return List.of(
            XpathBuilder
                .from(div, withClass("elite-meta"))
                .child(div)
                .child(span, atIndex(1))
                .build()
        );
    }

    @Override
    public Collection<By> ipAddressElements() {
        return List.of(
            XpathBuilder
                .from(div, withClass("elite-meta"))
                .child(div)
                .child(span, atIndex(1))
                .build()
        );
    }

    @Override
    public Collection<By> torrentPasskeyElements() {
        return List.of(
            By.id("passkeyDisplay")
        );
    }

    @Override
    protected By logoutButtonSelector() {
        openUserDropdownMenu();
        return XpathBuilder
            .from(li, withClass("user-footer"))
            .child(a, atIndex(2))
            .build();
    }

    private void openUserDropdownMenu() {
        LOGGER.debug("\t\t- Clicking user dropdown menu to make profile/logout button interactable");
        final By profileParentSelector = XpathBuilder
            .from(li, withClass("user-menu"))
            .child(a, withClass("nav-link"))
            .build();
        final WebElement profileParent = driver().findElement(profileParentSelector);
        clickButton(profileParent);

        LOGGER.trace("User dropdown menu clicked, waiting for pop-up to open");
        final By profileLinkSelector = XpathBuilder
            .from(li, withClass("user-footer"))
            .child(a, atIndex(1))
            .build();
        browserInteractionHelper().waitForElementToBeInteractable(profileLinkSelector, pageTransitionsDuration());
    }
}
