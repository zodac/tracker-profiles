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
import static net.zodac.tracker.framework.xpath.HtmlElement.input;
import static net.zodac.tracker.framework.xpath.HtmlElement.span;
import static net.zodac.tracker.framework.xpath.HtmlElement.table;
import static net.zodac.tracker.framework.xpath.HtmlElement.td;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.atIndex;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.withAttribute;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.withClass;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.withId;

import net.zodac.tracker.framework.TrackerType;
import net.zodac.tracker.framework.annotation.TrackerHandler;
import net.zodac.tracker.framework.xpath.NamedHtmlElement;
import net.zodac.tracker.framework.xpath.XpathBuilder;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

/**
 * Implementation of {@link AbstractTrackerHandler} for the {@code DigitalCore.Club} tracker.
 */
@TrackerHandler(name = "DigitalCore.Club", type = TrackerType.MANUAL, url = {
    "https://digitalcore.club/",
    "https://prxy.digitalcore.club/"
})
public class DigitalCoreClub extends AbstractTrackerHandler {

    @Override
    protected By usernameFieldSelector() {
        return XpathBuilder
            .from(div, withClass("panel-body"))
            .child(form, atIndex(1))
            .child(div, atIndex(1))
            .child(input, atIndex(1))
            .build();
    }

    @Override
    protected By passwordFieldSelector() {
        return XpathBuilder
            .from(div, withClass("panel-body"))
            .child(form, atIndex(1))
            .child(div, atIndex(2))
            .child(input, atIndex(1))
            .build();
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * For {@link DigitalCoreClub}, prior to clicking the login button with a successful username/password there is another field where a
     * Captcha needs to be entered.
     *
     * <p>
     * Manual user interaction:
     * <ol>
     *     <li>Enter correct captcha value</li>
     * </ol>
     */
    @Override
    protected void preLoginClickAction() {
        LOGGER.info("\t\t >>> Waiting for user to enter captcha");

        final By captchaSelector = XpathBuilder
            .from(div, withClass("neural-captcha-group"))
            .build();
        final WebElement captchaElement = driver().findElement(captchaSelector);
        browserInteractionHelper().highlightElement(captchaElement);
        displayUtils().confirm(trackerDefinition().name(), "Solve the captcha");
    }

    @Override
    protected By loginButtonSelector() {
        return XpathBuilder
            .from(button, withAttribute("translate", "LOGIN.TITLE"))
            .build();
    }

    @Override
    protected By profileLinkSelector() {
        return XpathBuilder
            .from(NamedHtmlElement.of("user"), atIndex(1))
            .descendant(a, atIndex(1))
            .build();
    }

    @Override
    protected By profilePageElementSelector() {
        return XpathBuilder
            .from(div, withId("contentContainer"))
            .descendant(table, atIndex(1))
            .descendant(td, withAttribute("translate", "STATUS.BONUS"))
            .build();
    }

    @Override
    protected By logoutButtonSelector() {
        return XpathBuilder
            .from(span, withAttribute("translate", "STATUS.LOG_OUT"))
            .build();
    }
}
