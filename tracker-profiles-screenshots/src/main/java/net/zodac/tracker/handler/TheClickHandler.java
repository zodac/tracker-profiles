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
import static net.zodac.tracker.framework.xpath.HtmlElement.span;
import static net.zodac.tracker.framework.xpath.HtmlElement.table;
import static net.zodac.tracker.framework.xpath.HtmlElement.tbody;
import static net.zodac.tracker.framework.xpath.HtmlElement.td;
import static net.zodac.tracker.framework.xpath.HtmlElement.tr;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.atIndex;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.withClass;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.withType;

import java.util.Collection;
import java.util.List;
import net.zodac.tracker.framework.TrackerType;
import net.zodac.tracker.framework.annotation.CommonTrackerHandler;
import net.zodac.tracker.framework.annotation.TrackerHandler;
import net.zodac.tracker.framework.xpath.NamedHtmlElement;
import net.zodac.tracker.framework.xpath.XpathBuilder;
import net.zodac.tracker.handler.redaction.RedactsIpAddress;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

/**
 * Common implementation of {@link AbstractTrackerHandler} for {@code .click} family of trackers.
 */
@CommonTrackerHandler("TheClick")
@TrackerHandler(name = "TheEmpire", type = TrackerType.MANUAL, url = "https://theempire.click/")
@TrackerHandler(name = "TheGeeks", type = TrackerType.MANUAL, url = "https://thegeeks.click/")
@TrackerHandler(name = "TheKitchen", type = TrackerType.MANUAL, url = "https://thekitchen.click/")
@TrackerHandler(name = "TheOccult", type = TrackerType.MANUAL, url = "https://theoccult.click/")
@TrackerHandler(name = "ThePlace", type = TrackerType.MANUAL, url = "https://theplace.click/")
@TrackerHandler(name = "TheShow", type = TrackerType.MANUAL, url = "https://theshow.click/")
@TrackerHandler(name = "TheVault", type = TrackerType.MANUAL, url = "https://thevault.click/")
public class TheClickHandler extends AbstractTrackerHandler implements RedactsIpAddress {

    /**
     * {@inheritDoc}
     *
     * <p>
     * For {@link TheClickHandler}, prior to clicking the login button with a captcha that needs to be clicked and verified.
     *
     * <p>
     * Manual user interaction:
     * <ol>
     *     <li>Click and pass the captcha</li>
     * </ol>
     */
    @Override
    protected void preLoginClickAction() {
        LOGGER.info("\t\t >>> Waiting for user to solve the captcha");

        final By captchaSelector = XpathBuilder
            .from(div, withClass("h-captcha"))
            .build();
        final WebElement captchaElement = driver.findElement(captchaSelector);
        browserInteractionHelper.highlightElement(captchaElement);
        displayUtils.confirm(trackerDefinition.name(), "Solve the captcha");
    }

    @Override
    protected By loginButtonSelector() {
        return XpathBuilder
            .from(input, withType("submit"))
            .build();
    }

    @Override
    protected By profileLinkSelector() {
        return XpathBuilder
            .from(span, withClass("statuslink"))
            .child(NamedHtmlElement.of("b"), atIndex(1))
            .child(a, atIndex(1))
            .build();
    }

    @Override
    protected By profilePageElementSelector() {
        return XpathBuilder
            .from(td, withClass("embedded"))
            .child(table)
            .child(tbody)
            .child(tr)
            .child(td, atIndex(2))
            .build();
    }

    @Override
    public Collection<By> ipAddressElements() {
        return List.of(
            XpathBuilder
                .from(td, withClass("embedded"))
                .child(table)
                .child(tbody)
                .child(tr)
                .child(td, atIndex(2))
                .build()
        );
    }

    @Override
    protected By logoutButtonSelector() {
        return XpathBuilder
            .from(span, withClass("statuslink"))
            .child(NamedHtmlElement.of("b"), atIndex(1))
            .child(a, atIndex(2))
            .build();
    }
}


