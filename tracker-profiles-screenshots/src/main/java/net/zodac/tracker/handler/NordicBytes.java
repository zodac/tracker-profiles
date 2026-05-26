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
import static net.zodac.tracker.framework.xpath.HtmlElement.footer;
import static net.zodac.tracker.framework.xpath.HtmlElement.form;
import static net.zodac.tracker.framework.xpath.HtmlElement.li;
import static net.zodac.tracker.framework.xpath.HtmlElement.table;
import static net.zodac.tracker.framework.xpath.HtmlElement.tbody;
import static net.zodac.tracker.framework.xpath.HtmlElement.td;
import static net.zodac.tracker.framework.xpath.HtmlElement.tr;
import static net.zodac.tracker.framework.xpath.HtmlElement.ul;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.atIndex;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.withClass;

import java.util.Collection;
import java.util.List;
import net.zodac.tracker.framework.annotation.TrackerHandler;
import net.zodac.tracker.framework.xpath.XpathBuilder;
import net.zodac.tracker.handler.definition.HasDismissibleElement;
import net.zodac.tracker.handler.redaction.RedactsIpAddress;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

/**
 * Implementation of {@link AbstractTrackerHandler} for the {@code NordicBytes} tracker.
 */
@TrackerHandler(name = "NordicBytes", url = "https://nordicbytes.org/")
public class NordicBytes extends AbstractTrackerHandler implements HasDismissibleElement, RedactsIpAddress {

    @Override
    protected By loginButtonSelector() {
        return XpathBuilder
            .from(form, atIndex(1))
            .child(button, atIndex(1))
            .build();
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * For {@link NordicBytes}, there is sometimes a pop-up with the latest site updates, which blocks links to the profile page. We'll check for
     * this, then close it if found.
     */
    @Override
    public void dismiss() {
        LOGGER.debug("\t\t- Checking for site changes modal");

        final By siteChangesSelector = XpathBuilder
            .from(footer, withClass("modal__footer"))
            .descendant(button, withClass("btn-primary"))
            .build();
        final Collection<WebElement> siteChangesElements = driver().findElements(siteChangesSelector);

        if (siteChangesElements.isEmpty()) {
            LOGGER.debug("\t\t\t- No site changes found");
            return;
        }

        LOGGER.debug("\t\t\t- Found {} site changes, clearing", siteChangesElements.size());
        for (final WebElement siteChangesElement : siteChangesElements) {
            clickButton(siteChangesElement);
        }

        LOGGER.debug("\t\t- Cleared site changes");
    }

    @Override
    protected By profileLinkSelector() {
        return XpathBuilder
            .from(div, withClass("header--user"))
            .child(div, atIndex(1))
            .child(div, atIndex(1))
            .child(ul, atIndex(1))
            .child(li, atIndex(1))
            .child(a, atIndex(1))
            .build();
    }

    @Override
    protected By profilePageElementSelector() {
        return XpathBuilder
            .from(div, withClass("__header-profile"))
            .build();
    }

    @Override
    public Collection<By> ipAddressElements() {
        return List.of(
            XpathBuilder
                .from(table)
                .child(tbody)
                .child(tr)
                .child(td, atIndex(2))
                .build()
        );
    }

    @Override
    protected By logoutButtonSelector() {
        return XpathBuilder
            .from(div, withClass("header--user"))
            .child(div, atIndex(1))
            .child(div, atIndex(1))
            .child(div, atIndex(1))
            .child(ul, atIndex(1))
            .child(li, atIndex(2))
            .child(form, atIndex(1))
            .child(button, atIndex(1))
            .build();
    }
}
