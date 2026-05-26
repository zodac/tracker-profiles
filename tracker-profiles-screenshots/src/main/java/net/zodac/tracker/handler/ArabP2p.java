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
import static net.zodac.tracker.framework.xpath.HtmlElement.img;
import static net.zodac.tracker.framework.xpath.HtmlElement.input;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.atIndex;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.withClass;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.withId;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.withType;
import static net.zodac.tracker.framework.xpath.XpathAxis.parent;

import java.util.List;
import net.zodac.tracker.framework.annotation.TrackerHandler;
import net.zodac.tracker.framework.xpath.XpathBuilder;
import net.zodac.tracker.handler.definition.HasFixedHeader;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

/**
 * Implementation of {@link AbstractTrackerHandler} for the {@code ArabP2P} tracker.
 */
@TrackerHandler(name = "ArabP2P", url = "https://www.arabp2p.net/")
public class ArabP2p extends AbstractTrackerHandler implements HasFixedHeader {

    @Override
    protected By usernameFieldSelector() {
        return By.id("want_username");
    }

    @Override
    protected By passwordFieldSelector() {
        return By.id("want_password");
    }

    @Override
    protected By loginButtonSelector() {
        return XpathBuilder
            .from(div, withId("bodyarea"))
            .descendant(form, atIndex(1))
            .child(div, atIndex(3))
            .descendant(input, withType("submit"))
            .build();
    }

    @Override
    protected By profileLinkSelector() {
        return XpathBuilder
            .from(img, withClass("toolbar_avatar"))
            .navigateTo(parent(a))
            .build();
    }

    @Override
    protected By profilePageElementSelector() {
        return By.id("userdeatils_tpl");
    }

    @Override
    public List<By> headerSelectors() {
        return List.of(
            XpathBuilder
                .from(div, withClass("toolbar_div"))
                .build()
        );
    }

    @Override
    protected By logoutButtonSelector() {
        // Highlight the nav bar to make the logout button interactable
        final By logoutParentSelector = profileLinkSelector();
        final WebElement logoutParent = driver().findElement(logoutParentSelector);
        browserInteractionHelper().moveTo(logoutParent);

        return XpathBuilder
            .from(div, withClass("toolbar_div"))
            .child(div, atIndex(2))
            .child(div, atIndex(1))
            .child(a, atIndex(8))
            .build();
    }
}
