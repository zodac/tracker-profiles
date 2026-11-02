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
import static net.zodac.tracker.framework.xpath.HtmlElement.span;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.withAttribute;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.withClass;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.withType;

import java.util.Collection;
import java.util.List;
import net.zodac.tracker.framework.annotation.TrackerHandler;
import net.zodac.tracker.framework.xpath.NamedHtmlElement;
import net.zodac.tracker.framework.xpath.XpathBuilder;
import net.zodac.tracker.util.BrowserInteractionHelper;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

/**
 * Extension of the {@link Unit3dHandler} for the {@code Concertos} tracker.
 */
@TrackerHandler(name = "Concertos", url = "https://concertos.live/")
public class Concertos extends Unit3dHandler {

    @Override
    protected By loginButtonSelector() {
        return XpathBuilder
            .from(button, withType("submit"))
            .build();
    }

    @Override
    protected By postLoginSelector() {
        return XpathBuilder
            .from(div, withClass("c-account"))
            .build();
    }

    @Override
    protected By profileLinkSelector() {
        openUserDropdownMenu();
        return XpathBuilder
            .from(a, withClass("c-account__name"))
            .build();
    }

    @Override
    protected By profilePageElementSelector() {
        return XpathBuilder
            .from(div, withClass("c-band__actions"))
            .build();
    }

    @Override
    public Collection<By> emailElements() {
        return List.of(
            XpathBuilder
                .from(NamedHtmlElement.of("aside"), withClass("c-profile__side"))
                .descendant(NamedHtmlElement.of("dd"))
                .build()
        );
    }

    @Override
    public Collection<By> ipAddressElements() {
        return List.of(
            XpathBuilder
                .from(span, withAttribute("data-label", "IP"))
                .build()
        );
    }

    @Override
    protected By logoutButtonSelector() {
        openUserDropdownMenu();
        return XpathBuilder
            .from(button, withClass("c-account__logout"))
            .build();
    }

    @Override
    protected void openUserDropdownMenu() {
        LOGGER.debug("\t\t- Waiting for login/rules pop-up to disappear");
        final BrowserInteractionHelper browserInteractionHelper = browserInteractionHelper();
        final By loginPopupSelector = By.id("swal2-title");
        browserInteractionHelper.waitForElementToDisappear(loginPopupSelector, pageLoadDuration());

        LOGGER.debug("\t\t- Highlighting user dropdown menu to make profile/logout button interactable");
        final By profileParentSelector = postLoginSelector();
        final WebElement profileParent = driver().findElement(profileParentSelector);
        browserInteractionHelper.moveTo(profileParent);
    }
}
