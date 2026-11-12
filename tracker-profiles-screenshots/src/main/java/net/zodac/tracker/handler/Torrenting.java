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
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.atIndex;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.withClass;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.withId;

import java.util.List;
import net.zodac.tracker.framework.TrackerType;
import net.zodac.tracker.framework.annotation.TrackerHandler;
import net.zodac.tracker.framework.xpath.XpathBuilder;
import net.zodac.tracker.handler.definition.HasCloudflareCheck;
import net.zodac.tracker.handler.definition.HasFixedHeader;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

/**
 * Implementation of {@link AbstractTrackerHandler} for the {@code Torrenting} tracker.
 */
@TrackerHandler(name = "Torrenting", type = TrackerType.MANUAL, url = "https://www.torrenting.com/")
public class Torrenting extends AbstractTrackerHandler implements HasCloudflareCheck, HasFixedHeader {

    @Override
    public By cloudflareSelector() {
        return XpathBuilder
            .from(div, withClass("cf-turnstile"))
            .build();
    }

    @Override
    protected By loginButtonSelector() {
        return XpathBuilder
            .from(div, withId("tab-login"))
            .descendant(button, atIndex(1))
            .build();
    }

    @Override
    protected By profileLinkSelector() {
        return XpathBuilder
            .from(a, withClass("hdr-stat"))
            .build();
    }

    @Override
    protected By profilePageElementSelector() {
        return XpathBuilder
            .from(div, withClass("up-header"))
            .build();
    }

    @Override
    public List<By> headerSelectors() {
        return List.of(
            By.tagName("header")
        );
    }

    @Override
    protected By logoutButtonSelector() {
        final By userDropdownSelector = XpathBuilder
            .from(div, withClass("hdr-dropdown"))
            .build();
        final WebElement userDropdown = driver().findElement(userDropdownSelector);
        browserInteractionHelper().addClass(userDropdown, "show");

        return XpathBuilder
            .from(div, withClass("hdr-dropdown"))
            .child(a, atIndex(5))
            .build();
    }
}
