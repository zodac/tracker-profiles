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
import static net.zodac.tracker.framework.xpath.HtmlElement.span;
import static net.zodac.tracker.framework.xpath.HtmlElement.tbody;
import static net.zodac.tracker.framework.xpath.HtmlElement.td;
import static net.zodac.tracker.framework.xpath.HtmlElement.tr;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.atIndex;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.withClass;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.withId;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.withType;

import java.util.Collection;
import java.util.List;
import net.zodac.tracker.framework.annotation.CommonTrackerHandler;
import net.zodac.tracker.framework.annotation.TrackerHandler;
import net.zodac.tracker.framework.xpath.NamedHtmlElement;
import net.zodac.tracker.framework.xpath.XpathBuilder;
import net.zodac.tracker.handler.definition.RedactsEmail;
import net.zodac.tracker.handler.definition.RedactsIpAddress;
import org.openqa.selenium.By;

/**
 * Common implementation of {@link AbstractTrackerHandler} for {@code TS Special Edition}-based trackers.
 */
@CommonTrackerHandler("TSSpecialEdition")
@TrackerHandler(name = "AussieRul.es", url = "https://aussierul.es/")
@TrackerHandler(name = "ImmortalSeed", url = "https://immortalseed.me/")
@TrackerHandler(name = "Tasmanit.es", url = "https://tasmanit.es/")
@TrackerHandler(name = "T3nnis", url = "https://t3nnis.tv/")
public class TsSpecialEditionHandler extends AbstractTrackerHandler implements RedactsEmail, RedactsIpAddress {

    @Override
    protected By usernameFieldSelector() {
        return XpathBuilder
            .from(input, withClass("inputUsernameLoginbox"))
            .build();
    }

    @Override
    protected By passwordFieldSelector() {
        return XpathBuilder
            .from(input, withClass("inputPasswordLoginbox"))
            .build();
    }

    @Override
    protected By loginButtonSelector() {
        return XpathBuilder
            .from(NamedHtmlElement.any(), withId("collapseobj_loginbox"))
            .descendant(form, atIndex(1))
            .child(input, withType("submit"))
            .build();
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * Depending on the specific {@link TsSpecialEditionHandler} tracker, the type HTML element with ID <b>collapseobj_loginbox</b> not consistent. In
     * addition, the user profile link is sometimes a direct child of this element, or sometimes nested below other HTML elements. This general XPATH
     * query should work for all trackers of this type.
     */
    @Override
    protected By profileLinkSelector() {
        return XpathBuilder
            .from(NamedHtmlElement.any(), withId("collapseobj_loginbox"))
            .descendant(a, atIndex(1))
            .build();
    }

    @Override
    protected By profilePageElementSelector() {
        return By.id("collapseobj_content2a");
    }

    @Override
    public Collection<By> emailElements() {
        return List.of(
            XpathBuilder
                .from(tbody)
                .child(tr, atIndex(1))
                .child(td, atIndex(1))
                .build()
        );
    }

    @Override
    public Collection<By> ipAddressElements() {
        return List.of(
            // IP address in profile sidebar
            XpathBuilder
                .from(tbody)
                .child(tr, atIndex(1))
                .child(td, atIndex(1))
                .build(),
            // IP address in header
            XpathBuilder
                .from(div, withId("top"))
                .child(div, atIndex(2))
                .child(span)
                .build()
        );
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * For {@link TsSpecialEditionHandler}, after clicking the logout button, a JavaScript alert appears, which must be accepted.
     */
    @Override
    protected void additionalActionAfterLogoutClick() {
        LOGGER.debug("\t\t- Clicking JavaScript alert to confirm logout");
        browserInteractionHelper.acceptAlert();
    }

    @Override
    protected By logoutButtonSelector() {
        return XpathBuilder
            .from(div, withId("top"))
            .child(div, atIndex(2))
            .child(span, atIndex(1))
            .child(a, atIndex(2))
            .build();
    }
}
