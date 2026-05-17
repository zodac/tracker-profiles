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
import static net.zodac.tracker.framework.xpath.HtmlElement.input;
import static net.zodac.tracker.framework.xpath.HtmlElement.table;
import static net.zodac.tracker.framework.xpath.HtmlElement.tbody;
import static net.zodac.tracker.framework.xpath.HtmlElement.td;
import static net.zodac.tracker.framework.xpath.HtmlElement.tr;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.atIndex;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.containsHref;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.withClass;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.withId;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.withName;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.withType;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import net.zodac.tracker.framework.annotation.TrackerHandler;
import net.zodac.tracker.framework.xpath.XpathBuilder;
import net.zodac.tracker.handler.definition.RedactsEmail;
import net.zodac.tracker.handler.definition.RedactsSensitiveInformation;
import org.openqa.selenium.By;

/**
 * Implementation of {@link AbstractTrackerHandler} for the {@code FunFile} tracker.
 */
@TrackerHandler(name = "FunFile", url = "https://www.funfile.org/")
public class FunFile extends AbstractTrackerHandler implements RedactsEmail, RedactsSensitiveInformation {

    @Override
    protected By loginButtonSelector() {
        return XpathBuilder
            .from(input, withName("login"), withType("submit"))
            .build();
    }

    @Override
    protected By profileLinkSelector() {
        return XpathBuilder
            .from(div, withId("avatar"))
            .child(a, atIndex(1))
            .build();
    }

    @Override
    protected By profilePageElementSelector() {
        return XpathBuilder
            .from(a, containsHref("mailto:"))
            .build();
    }

    @Override
    public Collection<By> emailElements() {
        return List.of(
            XpathBuilder
                .from(td, withClass("mf_content"))
                .child(table, atIndex(1))
                .child(tbody, atIndex(1))
                .child(tr, atIndex(3))
                .child(td, atIndex(2))
                .child(a, atIndex(1))
                .build()
        );
    }

    @Override
    public Map<String, By> sensitiveElements() {
        // TODO: Split this into IP and ISP?
        return Map.of(
            "IP and ISP", XpathBuilder
                .from(td, withClass("mf_content"))
                .child(table, atIndex(1))
                .child(tbody, atIndex(1))
                .child(tr, atIndex(4))
                .child(td, atIndex(2))
                .build()
        );
    }

    @Override
    protected By logoutButtonSelector() {
        return XpathBuilder
            .from(div, withClass("mb_content"))
            .child(table, atIndex(1))
            .child(tbody, atIndex(1))
            .child(tr, atIndex(7))
            .child(td, atIndex(1))
            .child(a, atIndex(1))
            .build();
    }
}
