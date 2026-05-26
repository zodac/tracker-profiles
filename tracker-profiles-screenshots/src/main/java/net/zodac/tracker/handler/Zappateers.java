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
import static net.zodac.tracker.framework.xpath.HtmlElement.li;
import static net.zodac.tracker.framework.xpath.HtmlElement.span;
import static net.zodac.tracker.framework.xpath.HtmlElement.table;
import static net.zodac.tracker.framework.xpath.HtmlElement.tbody;
import static net.zodac.tracker.framework.xpath.HtmlElement.td;
import static net.zodac.tracker.framework.xpath.HtmlElement.tr;
import static net.zodac.tracker.framework.xpath.HtmlElement.ul;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.atIndex;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.withClass;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.withId;

import java.util.Collection;
import java.util.List;
import net.zodac.tracker.framework.annotation.TrackerHandler;
import net.zodac.tracker.framework.xpath.XpathBuilder;
import net.zodac.tracker.handler.definition.HasDismissibleElement;
import net.zodac.tracker.handler.definition.HasFixedHeader;
import net.zodac.tracker.handler.definition.HasFixedSidebar;
import net.zodac.tracker.handler.definition.HasJumpButtons;
import net.zodac.tracker.handler.redaction.RedactsEmail;
import net.zodac.tracker.util.BrowserInteractionHelper;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

/**
 * Implementation of {@link Zappateers} for the {@code Zappateers} tracker.
 */
@TrackerHandler(name = "Zappateers", url = "https://zappateers.com/")
public class Zappateers extends AbstractTrackerHandler
    implements HasDismissibleElement, HasFixedHeader, HasFixedSidebar, HasJumpButtons, RedactsEmail {

    /**
     * {@inheritDoc}
     *
     * <p>
     * For {@link Zappateers}, the login button uses a different class than normal.
     */
    @Override
    protected By loginButtonSelector() {
        return XpathBuilder
            .from(button, withId("login-button"))
            .build();
    }

    @Override
    protected By postLoginSelector() {
        return XpathBuilder
            .from(div, withClass("hoe-right-header"))
            .build();
    }

    @Override
    public void dismiss() {
        LOGGER.debug("\t\t- Waiting for login/rules pop-up to disappear");
        final BrowserInteractionHelper browserInteractionHelper = browserInteractionHelper();
        final By loginPopupSelector = By.id("swal2-title");
        browserInteractionHelper.waitForElementToDisappear(loginPopupSelector, pageLoadDuration());

        // Cookie banner
        final By cookieSelector = XpathBuilder
            .from(div, withId("alert_system_notice"))
            .descendant(button, atIndex(1))
            .build();
        final WebElement cookieButton = driver().findElement(cookieSelector);
        clickButton(cookieButton);

        // Move the mouse, or else a dropdown menu is highlighted and covers some of the page
        browserInteractionHelper.moveToOrigin();
    }

    @Override
    protected By profileLinkSelector() {
        openUserDropdownMenu();
        return XpathBuilder
            .from(ul, withClass("dropdown-menu"))
            .child(li, atIndex(1))
            .child(a, atIndex(1))
            .build();
    }

    @Override
    protected By profilePageElementSelector() {
        return XpathBuilder
            .from(span, withClass("badge-user"))
            .build();
    }

    @Override
    public List<By> headerSelectors() {
        return List.of(XpathBuilder
            .from(div, withClass("hoe-right-header"))
            .build());
    }

    @Override
    public List<By> jumpButtonSelectors() {
        return List.of(
            By.id("back-to-top"),
            By.id("back-to-down")
        );
    }

    @Override
    public Collection<By> emailElements() {
        return List.of(
            XpathBuilder
                .from(table)
                .child(tbody, atIndex(1))
                .child(tr, atIndex(5))
                .child(td, atIndex(2))
                .build()
        );
    }

    @Override
    protected By logoutButtonSelector() {
        openUserDropdownMenu();
        return XpathBuilder
            .from(ul, withClass("dropdown-menu"))
            .child(li, atIndex(6))
            .child(a, atIndex(1))
            .build();
    }

    private void openUserDropdownMenu() {
        LOGGER.debug("\t\t- Clicking user dropdown menu to make profile/logout button interactable");
        final By profileParentSelector = XpathBuilder
            .from(li, withClass("hoe-header-profile"))
            .child(a, withClass("dropdown-toggle"))
            .build();
        final WebElement profileParent = driver().findElement(profileParentSelector);
        clickButton(profileParent);
    }
}
