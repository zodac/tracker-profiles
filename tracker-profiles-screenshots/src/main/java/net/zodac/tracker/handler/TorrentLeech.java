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

import static net.zodac.tracker.framework.xpath.HtmlElement.button;
import static net.zodac.tracker.framework.xpath.HtmlElement.div;
import static net.zodac.tracker.framework.xpath.HtmlElement.span;
import static net.zodac.tracker.framework.xpath.HtmlElement.table;
import static net.zodac.tracker.framework.xpath.HtmlElement.tbody;
import static net.zodac.tracker.framework.xpath.HtmlElement.td;
import static net.zodac.tracker.framework.xpath.HtmlElement.tr;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.atIndex;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.withAttribute;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.withClass;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.withType;

import java.util.Collection;
import java.util.List;
import net.zodac.tracker.framework.annotation.TrackerHandler;
import net.zodac.tracker.framework.xpath.NamedHtmlElement;
import net.zodac.tracker.framework.xpath.XpathBuilder;
import net.zodac.tracker.handler.definition.HasDismissibleElement;
import net.zodac.tracker.handler.definition.RedactsEmail;
import net.zodac.tracker.handler.definition.RedactsTorrentPasskey;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

/**
 * Implementation of {@link AbstractTrackerHandler} for the {@code TorrentLeech} tracker.
 */
@TrackerHandler(name = "TorrentLeech", url = {
    "https://www.torrentleech.org/",
    "https://www.torrentleech.cc/",
    "https://www.torrentleech.me/",
    "https://www.tleechreload.org/",
    "https://www.tlgetin.cc/"
})
public class TorrentLeech extends AbstractTrackerHandler implements HasDismissibleElement, RedactsEmail, RedactsTorrentPasskey {

    @Override
    protected By loginButtonSelector() {
        return XpathBuilder
            .from(button, withType("submit"))
            .build();
    }

    @Override
    protected By profileLinkSelector() {
        return XpathBuilder
            .from(span, withClass("user_superuser"))
            .build();
    }

    @Override
    protected By profilePageElementSelector() {
        return XpathBuilder
            .from(div, withClass("user-profile-container"))
            .build();
    }

    @Override
    public void dismiss() {
        // IP address warning banner
        final By cookieSelector = XpathBuilder
            .from(button, withClass("close"), withAttribute("title", "Dismiss"))
            .build();
        final WebElement cookieButton = driver.findElement(cookieSelector);
        clickButton(cookieButton);

        // Move the mouse, or else a dropdown menu is highlighted and covers some of the page
        browserInteractionHelper.moveToOrigin();
    }

    @Override
    public Collection<By> emailElements() {
        return List.of(
            XpathBuilder
                .from(table, withClass("profileViewTable"))
                .child(tbody, atIndex(1))
                .child(tr, atIndex(2))
                .child(td, atIndex(2))
                .build()
        );
    }

    @Override
    public Collection<By> torrentPasskeyElements() {
        return List.of(
            XpathBuilder
                .from(table, withClass("profileViewTable"))
                .child(tbody, atIndex(1))
                .child(tr, atIndex(10))
                .child(td, atIndex(2))
                .build()
        );
    }

    @Override
    protected By logoutButtonSelector() {
        return XpathBuilder
            .from(NamedHtmlElement.of("i"), withClass("fa-sign-out"))
            .build();
    }
}
