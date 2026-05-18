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
import net.zodac.tracker.framework.xpath.XpathBuilder;
import net.zodac.tracker.handler.definition.HasFixedHeader;
import net.zodac.tracker.handler.redaction.RedactsIrcPasskey;
import org.openqa.selenium.By;

/**
 * Extension of the {@link LuminanceHandler} for the {@code Libble} tracker.
 */
@TrackerHandler(name = "Libble", url = "https://libble.me/")
public class Libble extends LuminanceHandler implements HasFixedHeader, RedactsIrcPasskey {

    @Override
    public By loginPageSelector() {
        return XpathBuilder
            .from(div, withId("menu"))
            .child(a, atIndex(2))
            .build();
    }

    @Override
    protected By loginButtonSelector() {
        return By.name("login");
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * For {@link Libble}, the {@code SOS} section loads in after the rest of the page, so we wait for this to ensure the page doesn't render any
     * additional elements after we begin redaction.
     */
    @Override
    protected By profilePageElementSelector() {
        return By.id("sos");
    }

    @Override
    public List<By> headerSelectors() {
        return List.of(By.id("header"));
    }

    @Override
    public Collection<By> ircPasskeyElements() {
        return List.of(
            XpathBuilder
                .from(div, withClass("sidebar"))
                .child(div, atIndex(4))
                .child(ul, atIndex(1))
                .child(li, atIndex(5))
                .build()
        );
    }

    @Override
    public Collection<By> torrentPasskeyElements() {
        return List.of(
            XpathBuilder
                .from(div, withClass("sidebar"))
                .child(div, atIndex(4))
                .child(ul, atIndex(1))
                .child(li, atIndex(4))
                .build()
        );
    }

    @Override
    protected By logoutButtonSelector() {
        return XpathBuilder
            .from(ul, withId("userinfo_username"))
            .child(li, atIndex(3))
            .child(a, atIndex(1))
            .build();
    }
}
