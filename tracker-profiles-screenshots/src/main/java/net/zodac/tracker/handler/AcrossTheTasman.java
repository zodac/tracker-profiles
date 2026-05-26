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
import static net.zodac.tracker.framework.xpath.HtmlElement.li;
import static net.zodac.tracker.framework.xpath.HtmlElement.ul;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.atIndex;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.withClass;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.withId;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.withType;

import net.zodac.tracker.framework.annotation.TrackerHandler;
import net.zodac.tracker.framework.xpath.XpathBuilder;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

/**
 * Implementation of {@link AbstractTrackerHandler} for the {@code AcrossTheTasman} tracker.
 */
@TrackerHandler(name = "AcrossTheTasman", url = "https://acrossthetasman.com/")
public class AcrossTheTasman extends AbstractTrackerHandler {

    @Override
    protected By usernameFieldSelector() {
        return By.id("navbar_username");
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * For {@link AcrossTheTasman}, the real password field ({@code navbar_password}) is hidden by JavaScript on page load, behind a placeholder hint
     * field ({@code navbar_password_hint}). Clicking the hint field triggers the JS swap that makes the real password field interactable.
     */
    @Override
    protected By passwordFieldSelector() {
        LOGGER.debug("\t\t- Clicking password hint field to reveal real password input");
        final By hintFieldSelector = By.id("navbar_password_hint");
        final WebElement hintField = driver().findElement(hintFieldSelector);
        clickButton(hintField);
        return By.id("navbar_password");
    }

    @Override
    protected By loginButtonSelector() {
        return XpathBuilder
            .from(input, withClass("loginbutton"), withType("submit"))
            .build();
    }

    @Override
    protected By postLoginSelector() {
        return XpathBuilder
            .from(ul, withClass("isuser"))
            .build();
    }

    @Override
    protected By profileLinkSelector() {
        return XpathBuilder
            .from(ul, withClass("isuser"))
            .child(li, atIndex(3))
            .child(a, atIndex(1))
            .build();
    }

    @Override
    protected By profilePageElementSelector() {
        return XpathBuilder
            .from(div, withId("userinfoblock"))
            .build();
    }

    @Override
    protected By logoutButtonSelector() {
        return XpathBuilder
            .from(ul, withClass("isuser"))
            .child(li, atIndex(1))
            .child(a, atIndex(1))
            .build();
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * For {@link AcrossTheTasman}, after clicking the logout button, a JavaScript alert appears, which must be accepted.
     */
    @Override
    protected void additionalActionAfterLogoutClick() {
        LOGGER.debug("\t\t- Clicking JavaScript alert to confirm logout");
        browserInteractionHelper().acceptAlert();
    }
}
