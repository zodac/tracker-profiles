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

import static net.zodac.tracker.framework.xpath.HtmlElement.button;
import static net.zodac.tracker.framework.xpath.HtmlElement.div;
import static net.zodac.tracker.framework.xpath.HtmlElement.span;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.atIndex;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.withClass;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.withType;

import net.zodac.tracker.framework.annotation.TrackerHandler;
import net.zodac.tracker.framework.xpath.XpathBuilder;
import net.zodac.tracker.handler.definition.DoesNotScrollDuringScreenshot;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

/**
 * Implementation of {@link AbstractTrackerHandler} for the {@code Milkie} tracker.
 */
@TrackerHandler(name = "Milkie", url = "https://milkie.cc/")
public class Milkie extends AbstractTrackerHandler implements DoesNotScrollDuringScreenshot {

    @Override
    protected By usernameFieldSelector() {
        return By.id("mat-input-0");
    }

    @Override
    protected By passwordFieldSelector() {
        return By.id("mat-input-1");
    }

    @Override
    protected By loginButtonSelector() {
        return XpathBuilder
            .from(button, withType("submit"))
            .build();
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * For {@link Milkie}, there is no user profile page. There is simply a pop-up with the user's stats. So we click the pop-up to display
     * stats but there is no new page loaded.
     */
    @Override
    protected By profileLinkSelector() {
        return XpathBuilder
            .from(button, withClass("profile-button"))
            .build();
    }

    @Override
    protected By profilePageElementSelector() {
        return By.tagName("tor-browse-list");
    }

    @Override
    protected By logoutButtonSelector() {
        openUserDropdownMenu();
        return XpathBuilder
            .from(div, withClass("cdk-overlay-pane"))
            .child(div, atIndex(2))
            .child(div, atIndex(1))
            .child(div, atIndex(2))
            .child(span, atIndex(2))
            .build();
    }

    private void openUserDropdownMenu() {
        LOGGER.debug("\t\t- Clicking user dropdown menu to make logout button interactable");
        reloadProfilePage();

        final By profileLinkSelector = profileLinkSelector();
        final WebElement logoutParent = browserInteractionHelper().waitForElementToBePresent(profileLinkSelector, maximumLinkResolutionDuration());
        clickButton(logoutParent);
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * For {@link Milkie}, the {@link #usernameFieldSelector()} is not valid, since after a logout the ID of the {@link WebElement} is
     * different. However, the {@link #loginButtonSelector()} is still the same, so we can reuse that.
     */
    @Override
    protected By postLogoutElementSelector() {
        return loginButtonSelector();
    }
}
