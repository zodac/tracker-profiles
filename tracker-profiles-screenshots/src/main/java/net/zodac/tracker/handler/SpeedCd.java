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

import net.zodac.tracker.framework.annotation.TrackerHandler;
import net.zodac.tracker.framework.xpath.XpathBuilder;
import net.zodac.tracker.handler.definition.HasCloudflareCheck;
import net.zodac.tracker.util.BrowserInteractionHelper;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

/**
 * Implementation of {@link AbstractTrackerHandler} for the {@code Speed.CD} tracker.
 */
@TrackerHandler(name = "Speed.CD", url = {
    "https://speed.cd/",
    "https://speed.click/",
    "https://speeders.me/",
})
public class SpeedCd extends AbstractTrackerHandler implements HasCloudflareCheck {

    @Override
    protected By passwordFieldSelector() {
        // The password field doesn't load until the username is entered and the 'Next' button is clicked
        final BrowserInteractionHelper browserInteractionHelper = browserInteractionHelper();
        final By usernameNextButtonSelector = XpathBuilder
            .from(form, withClass("login"), atIndex(1))
            .child(div, atIndex(2))
            .child(input, atIndex(1))
            .build();
        final WebElement usernameNextButton = browserInteractionHelper.waitForElementToBeInteractable(usernameNextButtonSelector, pageLoadDuration());
        clickButton(usernameNextButton);

        LOGGER.trace("Waiting for username to be accepted and password field to appear");
        final By passwordFieldSelector = By.name("pwd");
        browserInteractionHelper.waitForElementToBeInteractable(passwordFieldSelector, pageLoadDuration());
        return passwordFieldSelector;
    }

    @Override
    protected By loginButtonSelector() {
        return XpathBuilder
            .from(form, withClass("login"), atIndex(2))
            .child(div, atIndex(2))
            .child(input, atIndex(1))
            .build();
    }

    @Override
    protected By profileLinkSelector() {
        return XpathBuilder
            .from(div, withClass("tSta"))
            .child(a, atIndex(1))
            .build();
    }

    @Override
    protected By profilePageElementSelector() {
        return By.id("Details");
    }

    @Override
    protected By logoutButtonSelector() {
        return XpathBuilder
            .from(input, withClass("logOut"))
            .build();
    }
}
