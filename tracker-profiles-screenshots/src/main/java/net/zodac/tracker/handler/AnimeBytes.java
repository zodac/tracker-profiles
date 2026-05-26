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
import static net.zodac.tracker.framework.xpath.HtmlElement.li;
import static net.zodac.tracker.framework.xpath.HtmlElement.span;
import static net.zodac.tracker.framework.xpath.HtmlElement.ul;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.atIndex;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.withId;

import net.zodac.tracker.framework.annotation.TrackerHandler;
import net.zodac.tracker.framework.xpath.XpathBuilder;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

/**
 * Implementation of {@link AbstractTrackerHandler} for the {@code AnimeBytes} tracker.
 */
@TrackerHandler(name = "AnimeBytes", url = "https://animebytes.tv/")
public class AnimeBytes extends AbstractTrackerHandler {

    @Override
    public By loginPageSelector() {
        return By.id("nav_login");
    }

    @Override
    protected By loginButtonSelector() {
        return By.name("login");
    }

    @Override
    protected By profilePageElementSelector() {
        return By.id("user_leftcol");
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * For {@link AnimeBytes}, after clicking the logout button, another button appears to confirm logout, which must be clicked.
     */
    @Override
    protected void additionalActionAfterLogoutClick() {
        LOGGER.debug("\t\t- Logout button clicked, clicking confirmation button");
        final By logoutConfirmSelector = By.name("yes");
        final WebElement logoutConfirm = browserInteractionHelper().waitForElementToBePresent(logoutConfirmSelector, pageTransitionsDuration());
        clickButton(logoutConfirm);
    }

    @Override
    protected By logoutButtonSelector() {
        // Click the user dropdown menu bar to make the logout button interactable
        final By logoutParentSelector = XpathBuilder
            .from(li, withId("username_menu"))
            .child(span, atIndex(1))
            .build();
        final WebElement logoutParent = driver().findElement(logoutParentSelector);
        clickButton(logoutParent);

        return XpathBuilder
            .from(li, withId("username_menu"))
            .child(ul, atIndex(1))
            .child(li, atIndex(11))
            .child(a, atIndex(1))
            .build();
    }
}
