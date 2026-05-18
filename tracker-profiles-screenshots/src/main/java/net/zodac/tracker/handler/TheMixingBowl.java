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
import static net.zodac.tracker.framework.xpath.HtmlElement.div;
import static net.zodac.tracker.framework.xpath.HtmlElement.li;
import static net.zodac.tracker.framework.xpath.HtmlElement.ul;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.atIndex;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.withClass;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.withId;

import java.util.Collection;
import java.util.List;
import net.zodac.tracker.framework.annotation.TrackerHandler;
import net.zodac.tracker.framework.xpath.NamedHtmlElement;
import net.zodac.tracker.framework.xpath.XpathBuilder;
import net.zodac.tracker.handler.redaction.RedactsIpAddress;
import net.zodac.tracker.handler.redaction.RedactsTorrentPasskey;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

/**
 * Implementation of {@link AbstractTrackerHandler} for the {@code TheMixingBowl} tracker.
 */
@TrackerHandler(name = "TheMixingBowl", url = "https://themixingbowl.org/")
public class TheMixingBowl extends AbstractTrackerHandler implements RedactsIpAddress, RedactsTorrentPasskey {

    @Override
    protected By loginPageSelector() {
        return XpathBuilder
            .from(div, withClass("shadow3"))
            .child(NamedHtmlElement.of("h3"), atIndex(1))
            .child(a, atIndex(1))
            .build();
    }

    @Override
    protected By loginButtonSelector() {
        return By.id("ok");
    }

    @Override
    protected By profileLinkSelector() {
        return XpathBuilder
            .from(li, withClass("userdrop"))
            .child(a, atIndex(1))
            .build();
    }

    @Override
    protected By profilePageElementSelector() {
        return By.id("joined");
    }

    @Override
    public Collection<By> ipAddressElements() {
        return List.of(
            // Browser IP address
            By.id("browseip"),
            // Peer IP addresses
            By.id("peerip")
        );
    }

    @Override
    public Collection<By> torrentPasskeyElements() {
        return List.of(
            By.id("passkey")
        );
    }

    @Override
    protected By logoutButtonSelector() {
        // Highlight the user profile link to make the logout button interactable
        final By logoutParentSelector = profileLinkSelector();
        final WebElement logoutParent = driver.findElement(logoutParentSelector);
        browserInteractionHelper.moveTo(logoutParent);

        return XpathBuilder
            .from(ul, withId("navie"))
            .child(li, atIndex(11))
            .child(a, atIndex(1))
            .build();
    }
}
