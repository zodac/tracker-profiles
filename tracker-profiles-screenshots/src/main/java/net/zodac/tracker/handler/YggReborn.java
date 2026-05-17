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
import static net.zodac.tracker.framework.xpath.HtmlElement.form;
import static net.zodac.tracker.framework.xpath.HtmlElement.header;
import static net.zodac.tracker.framework.xpath.HtmlElement.main;
import static net.zodac.tracker.framework.xpath.HtmlElement.span;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.atIndex;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.containsHref;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.withAttribute;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.withClass;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.withType;
import static net.zodac.tracker.framework.xpath.XpathAxis.parent;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import net.zodac.tracker.framework.annotation.TrackerHandler;
import net.zodac.tracker.framework.xpath.XpathBuilder;
import net.zodac.tracker.handler.definition.HasFixedHeader;
import net.zodac.tracker.handler.definition.RedactsEmail;
import net.zodac.tracker.handler.definition.RedactsTorrentPasskey;
import net.zodac.tracker.util.BrowserInteractionHelper;
import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;

/**
 * Implementation of {@link AbstractTrackerHandler} for the {@code YggReborn} tracker.
 */
@TrackerHandler(name = "YggReborn", url = "https://www.yggreborn.org/")
public class YggReborn extends AbstractTrackerHandler implements HasFixedHeader, RedactsEmail, RedactsTorrentPasskey {

    @Override
    protected By usernameFieldSelector() {
        BrowserInteractionHelper.explicitWait(Duration.ofSeconds(1L), "web page to complete translation");
        return By.id("identifier");
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
            .from(a, containsHref("/account/"))
            .build();
    }

    @Override
    protected By profilePageElementSelector() {
        return By.id("announce-url");
    }

    @Override
    public List<By> headerSelectors() {
        return List.of(XpathBuilder
            .from(div, withClass("header-shell"))
            .navigateTo(parent(header))
            .build()
        );
    }

    @Override
    public Collection<By> emailElements() {
        return List.of(
            XpathBuilder
                .from(main, atIndex(1))
                .child(div, atIndex(4))
                .child(div, atIndex(1))
                .child(div, atIndex(2))
                .child(span, atIndex(2))
                .build()
        );
    }

    @Override
    public Collection<By> torrentPasskeyElements() {
        return List.of(
            By.id("announce-url")
        );
    }

    /**
     * {@inheritDoc}
     * 
     * <p>
     * For {@link YggReborn}, we must be on the profile page, as the logout button is at the bottom. We scroll it into view before clicking.
     */
    @Override
    protected By logoutButtonSelector() {
        try {
            LOGGER.trace("Confirming we are still on the profile page");
            driver.findElement(profilePageElementSelector());
        } catch (final NoSuchElementException e) {
            LOGGER.debug("Could not find profile page element, unable to log out", e);
            LOGGER.warn("Could not find profile page element, unable to log out: {}", e.getMessage());
            throw e;
        }

        final By logoutButtonSelector = XpathBuilder
            .from(form, withAttribute("method", "post"))
            .child(button, atIndex(1))
            .build();
        browserInteractionHelper.scrollToElement(driver.findElement(logoutButtonSelector));
        return logoutButtonSelector;
    }
}
