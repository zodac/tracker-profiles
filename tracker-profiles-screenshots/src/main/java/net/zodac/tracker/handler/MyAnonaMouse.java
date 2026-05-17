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
import static net.zodac.tracker.framework.xpath.HtmlElement.img;
import static net.zodac.tracker.framework.xpath.HtmlElement.input;
import static net.zodac.tracker.framework.xpath.HtmlElement.li;
import static net.zodac.tracker.framework.xpath.HtmlElement.table;
import static net.zodac.tracker.framework.xpath.HtmlElement.tbody;
import static net.zodac.tracker.framework.xpath.HtmlElement.td;
import static net.zodac.tracker.framework.xpath.HtmlElement.tr;
import static net.zodac.tracker.framework.xpath.HtmlElement.ul;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.atIndex;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.containsFlag;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.withClass;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.withId;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.withType;

import java.util.Collection;
import java.util.List;
import net.zodac.tracker.framework.annotation.TrackerHandler;
import net.zodac.tracker.framework.xpath.XpathBuilder;
import net.zodac.tracker.handler.definition.RedactsIpAddress;
import net.zodac.tracker.redaction.RedactionBuffer;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

/**
 * Implementation of {@link AbstractTrackerHandler} for the {@code MyAnonaMouse} tracker.
 */
@TrackerHandler(name = "MyAnonaMouse", url = "https://www.myanonamouse.net/")
public class MyAnonaMouse extends AbstractTrackerHandler implements RedactsIpAddress {

    @Override
    protected By usernameFieldSelector() {
        return By.name("email");
    }

    @Override
    protected By loginButtonSelector() {
        return XpathBuilder
            .from(input, withType("submit"))
            .build();
    }

    @Override
    protected By postLoginSelector() {
        return By.id("userStat");
    }

    @Override
    protected By profileLinkSelector() {
        openUserDropdownMenu();
        return XpathBuilder
            .from(a, withClass("myInfo"))
            .build();
    }

    @Override
    protected By profilePageElementSelector() {
        return XpathBuilder
            .from(img, containsFlag())
            .build();
    }

    @Override
    public Collection<By> ipAddressElements() {
        return List.of(
            XpathBuilder
                .from(div, withClass("blockBodyCon"))
                .child(table, atIndex(1))
                .child(tbody, atIndex(1))
                .child(tr, atIndex(3))
                .child(td, atIndex(2))
                .build()
        );
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * The redaction doesn't cover the full {@code <li>} element for some reason, so we extend it to the left.
     */
    @Override
    public RedactionBuffer ipAddressElementBuffer() {
        return RedactionBuffer.withLeftOffset(13);
    }

    @Override
    protected By logoutButtonSelector() {
        openUserDropdownMenu();
        return XpathBuilder
            .from(ul, withId("menu"))
            .child(li, atIndex(2))
            .child(ul, atIndex(1))
            .child(li, atIndex(16))
            .child(a, atIndex(1))
            .build();
    }

    private void openUserDropdownMenu() {
        LOGGER.debug("\t\t- Highlighting user dropdown menu to make profile/logout button interactable");
        final By profileDropDownSelector = By.id("userMenu");
        final WebElement profileDropDown = driver.findElement(profileDropDownSelector);
        browserInteractionHelper.moveTo(profileDropDown);
    }
}
