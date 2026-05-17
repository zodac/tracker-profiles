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
import static net.zodac.tracker.framework.xpath.HtmlElement.p;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.atIndex;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.withClass;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.withId;

import java.util.Collection;
import java.util.List;
import net.zodac.tracker.framework.annotation.TrackerHandler;
import net.zodac.tracker.framework.xpath.XpathBuilder;
import net.zodac.tracker.handler.definition.RedactsIrcPasskey;
import org.openqa.selenium.By;

/**
 * Implementation of {@link AbstractTrackerHandler} for the {@code Fappaizuri} tracker.
 */
@TrackerHandler(name = "Fappaizuri", adult = true, url = "https://fappaizuri.me/")
public class Fappaizuri extends AbstractTrackerHandler implements RedactsIrcPasskey {

    @Override
    protected By passwordFieldSelector() {
        return By.id("password-input");
    }

    @Override
    protected By loginButtonSelector() {
        return By.id("lgbnt");
    }

    @Override
    protected By profileLinkSelector() {
        return XpathBuilder
            .from(div, withClass("myBlock-content"))
            .child(div, atIndex(1))
            .child(div, atIndex(1))
            .child(a, atIndex(1))
            .build();
    }

    @Override
    protected By profilePageElementSelector() {
        return XpathBuilder
            .from(div, withClass("myFrame-content"))
            .descendant(div, withClass("box"))
            .child(div, atIndex(1))
            .child(div, atIndex(2))
            .child(p, atIndex(3))
            .build();
    }

    @Override
    public Collection<By> ircPasskeyElements() {
        return List.of(
            XpathBuilder
                .from(div, withClass("myFrame-content"))
                .descendant(div, withClass("box"))
                .child(div, atIndex(1))
                .child(div, atIndex(2))
                .child(p, atIndex(3))
                .build()
        );
    }

    @Override
    protected By logoutButtonSelector() {
        return XpathBuilder
            .from(div, withId("icons-position"))
            .child(a, atIndex(4))
            .build();
    }
}
