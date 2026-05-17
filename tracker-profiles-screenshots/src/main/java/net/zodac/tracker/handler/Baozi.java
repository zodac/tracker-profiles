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
import static net.zodac.tracker.framework.xpath.HtmlElement.form;
import static net.zodac.tracker.framework.xpath.HtmlElement.input;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.atIndex;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.withClass;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.withId;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.withType;

import java.util.Collection;
import java.util.List;
import net.zodac.tracker.framework.TrackerType;
import net.zodac.tracker.framework.annotation.TrackerHandler;
import net.zodac.tracker.framework.xpath.NamedHtmlElement;
import net.zodac.tracker.framework.xpath.XpathBuilder;
import net.zodac.tracker.handler.definition.HasCloudflareCheck;
import org.openqa.selenium.By;

/**
 * Extension of the {@link NexusPhpHandler} for the {@code Baozi} tracker.
 */
@TrackerHandler(name = "Baozi", type = TrackerType.MANUAL, url = "https://p.t-baozi.cc/")
public class Baozi extends NexusPhpHandler implements HasCloudflareCheck {

    @Override
    protected By languageDropdownSelector() {
        return XpathBuilder
            .from(div, withId("lang_div"))
            .child(form, atIndex(1))
            .child(div, atIndex(1))
            .child(NamedHtmlElement.of("select"), atIndex(1))
            .build();
    }

    @Override
    protected By passwordFieldSelector() {
        return XpathBuilder
            .from(input, withClass("password"), withType("password"))
            .build();
    }

    @Override
    protected By loginButtonSelector() {
        return By.id("submit-btn");
    }

    @Override
    protected By profileLinkSelector() {
        return XpathBuilder
            .from(a, withClass("User_Name"))
            .build();
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * For {@link Baozi}, the IP address is already blurred and not visible in the screenshot.
     */
    @Override
    public Collection<By> ipAddressElements() {
        return List.of();
    }

    @Override
    protected By logoutButtonSelector() {
        return XpathBuilder
            .from(a, withClass("logout"))
            .build();
    }
}
