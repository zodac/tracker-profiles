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
import static net.zodac.tracker.framework.xpath.HtmlElement.form;
import static net.zodac.tracker.framework.xpath.HtmlElement.img;
import static net.zodac.tracker.framework.xpath.HtmlElement.li;
import static net.zodac.tracker.framework.xpath.HtmlElement.nav;
import static net.zodac.tracker.framework.xpath.HtmlElement.ul;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.atIndex;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.containsFlag;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.containsHref;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.withAttribute;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.withClass;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.withType;
import static net.zodac.tracker.framework.xpath.XpathAxis.precedingSibling;

import java.util.List;
import net.zodac.tracker.framework.annotation.TrackerHandler;
import net.zodac.tracker.framework.xpath.NamedHtmlElement;
import net.zodac.tracker.framework.xpath.XpathBuilder;
import net.zodac.tracker.handler.definition.HasFixedHeader;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

/**
 * Implementation of {@link AbstractTrackerHandler} for the {@code F1GP} tracker.
 */
@TrackerHandler(name = "F1GP", url = "https://f1gp.site/")
public class F1Gp extends AbstractTrackerHandler implements HasFixedHeader {

    /**
     * {@inheritDoc}
     *
     * <p>
     * For {@link F1Gp}, the login form is inside a dropdown on the homepage. The dropdown toggle must be clicked first to reveal the username and
     * password fields.
     */
    @Override
    protected By usernameFieldSelector() {
        LOGGER.debug("\t\t- Clicking 'login' form to make username/password fields interactable");
        final By loginFormSelector = XpathBuilder
            .from(form, withAttribute("action", "index.php?page=login"))
            .navigateTo(precedingSibling(button))
            .build();
        final WebElement loginForm = browserInteractionHelper().waitForElementToBeInteractable(loginFormSelector, pageLoadDuration());
        clickButton(loginForm);
        return By.id("username");
    }

    @Override
    protected By passwordFieldSelector() {
        return By.id("password");
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
            .from(NamedHtmlElement.of("h6"), atIndex(1))
            .descendant(a, containsHref("page=userdetails"))
            .build();
    }

    @Override
    protected By profilePageElementSelector() {
        return XpathBuilder
            .from(img, containsFlag())
            .build();
    }

    @Override
    public List<By> headerSelectors() {
        return List.of(XpathBuilder
            .from(nav, atIndex(1))
            .build());
    }

    @Override
    protected By logoutButtonSelector() {
        return XpathBuilder
            .from(ul, withClass("level1"))
            .child(li, atIndex(1))
            .child(a, atIndex(1))
            .build();
    }
}
