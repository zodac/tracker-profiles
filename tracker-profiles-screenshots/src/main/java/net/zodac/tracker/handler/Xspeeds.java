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
import static net.zodac.tracker.framework.xpath.HtmlElement.li;
import static net.zodac.tracker.framework.xpath.HtmlElement.ul;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.atIndex;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.withClass;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.withId;

import net.zodac.tracker.framework.annotation.TrackerHandler;
import net.zodac.tracker.framework.xpath.NamedHtmlElement;
import net.zodac.tracker.framework.xpath.XpathBuilder;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

/**
 * Extension of the {@link TsSpecialEditionHandler} for the {@code XSpeeds} tracker.
 */
@TrackerHandler(name = "XSpeeds", url = "https://www.xspeeds.eu/")
public class Xspeeds extends TsSpecialEditionHandler {

    /**
     * {@inheritDoc}
     *
     * <p>
     * For {@link Xspeeds}, if the user has any PMs we are taken to the inbox. If that's the case, we'll redirect back to the homepage so the normal
     * flow can continue.
     */
    @Override
    public void postLoginClickAction() {
        final String currentPage = driver().getCurrentUrl();
        LOGGER.debug("\t\t- Checking current page: {}", currentPage);

        if (!currentPage.endsWith("messages.php")) {
            LOGGER.debug("\t\t- Not on messages page, proceeding as normal");
            return;
        }

        LOGGER.debug("\t\t- On messages page, redirecting to home page");
        final By homeLinkSelector = XpathBuilder
            .from(div, withId("menu"))
            .child(ul, withClass("TSSEMenu"))
            .child(li, atIndex(1))
            .child(a, atIndex(1))
            .build();
        final WebElement homeLink = driver().findElement(homeLinkSelector);
        clickButton(homeLink);

        LOGGER.debug("\t\t- Redirected to {}", driver().getCurrentUrl());
    }

    @Override
    protected By profileLinkSelector() {
        return XpathBuilder
            .from(NamedHtmlElement.any(), withId("collapseobj_userstats"))
            .descendant(a, atIndex(1))
            .build();
    }
}
