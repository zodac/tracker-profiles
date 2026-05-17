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
import static net.zodac.tracker.framework.xpath.HtmlElement.li;
import static net.zodac.tracker.framework.xpath.HtmlElement.span;
import static net.zodac.tracker.framework.xpath.HtmlElement.table;
import static net.zodac.tracker.framework.xpath.HtmlElement.td;
import static net.zodac.tracker.framework.xpath.HtmlElement.ul;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.atIndex;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.withClass;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.withId;

import java.util.Collection;
import java.util.List;
import net.zodac.tracker.framework.TrackerType;
import net.zodac.tracker.framework.annotation.TrackerHandler;
import net.zodac.tracker.framework.xpath.XpathBuilder;
import net.zodac.tracker.handler.definition.RedactsEmail;
import net.zodac.tracker.handler.definition.RedactsIpAddress;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

/**
 * Implementation of {@link AbstractTrackerHandler} for the {@code GazelleGames} tracker.
 */
@TrackerHandler(name = "GazelleGames", type = TrackerType.MANUAL, url = "https://gazellegames.net/")
public class GazelleGames extends AbstractTrackerHandler implements RedactsEmail, RedactsIpAddress {

    /**
     * {@inheritDoc}
     *
     * <p>
     * For {@link GazelleGames}, prior to clicking the login button with a successful username/password there is a multiple-choice question,
     * where the correct game title must be chosen that matches the picture.
     *
     * <p>
     * Manual user interaction:
     * <ol>
     *     <li>Select correct answer to question</li>
     * </ol>
     */
    @Override
    protected void preLoginClickAction() {
        LOGGER.info("\t\t >>> Waiting for user to select correct game title");

        final By selectionSelector = XpathBuilder
            .from(div, withId("tdwrap"))
            .child(form, atIndex(1))
            .child(table, atIndex(1))
            .build();
        final WebElement selectionElement = driver.findElement(selectionSelector);
        browserInteractionHelper.highlightElement(selectionElement);
        displayUtils.confirm(trackerDefinition.name(), "Select the correct game");
    }

    @Override
    protected By loginButtonSelector() {
        return XpathBuilder
            .from(td, withId("login_td"))
            .child(input, atIndex(1))
            .build();
    }

    @Override
    protected By profilePageElementSelector() {
        return By.id("user_stats");
    }

    @Override
    public Collection<By> emailElements() {
        return List.of(
            XpathBuilder
                .from(ul, withClass("stats"))
                .child(li)
                .child(a, atIndex(1))
                .build()
        );
    }

    @Override
    public Collection<By> ipAddressElements() {
        return List.of(
            // Footer with last used IP address
            XpathBuilder
                .from(div, withId("copyright"))
                .descendant(span)
                .build()
        );
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * For {@link GazelleGames}, after clicking the logout button, a JavaScript alert appears, which must be accepted.
     */
    @Override
    protected void additionalActionAfterLogoutClick() {
        LOGGER.debug("\t\t- Clicking JavaScript alert to confirm logout");
        browserInteractionHelper.acceptAlert();
    }

    @Override
    protected By logoutButtonSelector() {
        return XpathBuilder
            .from(li, withId("nav_logout"))
            .child(a, atIndex(1))
            .build();
    }
}
