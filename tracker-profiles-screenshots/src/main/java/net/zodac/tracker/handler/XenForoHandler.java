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
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.atIndex;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.withClass;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.withType;

import net.zodac.tracker.framework.annotation.CommonTrackerHandler;
import net.zodac.tracker.framework.xpath.XpathBuilder;
import net.zodac.tracker.util.BrowserInteractionHelper;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

/**
 * Common implementation of {@link AbstractTrackerHandler} for the {@code XenForo}-based trackers.
 *
 * <p>
 * Note that the URLs for implementations should be set to the login page, due to the login button opening a pop-up instead of a new page.
 */
@CommonTrackerHandler("XenForo")
public class XenForoHandler extends AbstractTrackerHandler {

    @Override
    protected By usernameFieldSelector() {
        return By.name("login");
    }

    @Override
    protected By loginButtonSelector() {
        return XpathBuilder
            .from(button, withType("submit"))
            .build();
    }

    @Override
    protected By postLoginSelector() {
        return XpathBuilder
            .from(a, withClass("p-navgroup-link--user"))
            .build();
    }

    @Override
    protected By profileLinkSelector() {
        navigateToUserPage();
        return XpathBuilder
            .from(div, withClass("p-body-sideNavContent"))
            .child(div, atIndex(1))
            .child(div, atIndex(1))
            .child(div, atIndex(1))
            .child(a, atIndex(1))
            .build();
    }

    @Override
    protected By profilePageElementSelector() {
        return XpathBuilder
            .from(span, withClass("memberHeader-nameWrapper"))
            .build();
    }

    @Override
    protected By logoutButtonSelector() {
        navigateToUserPage();
        return XpathBuilder
            .from(div, withClass("p-body-sideNavContent"))
            .child(div, atIndex(2))
            .child(div, atIndex(1))
            .child(div, atIndex(1))
            .child(a, atIndex(1))
            .build();
    }

    private void navigateToUserPage() {
        // Click the nav bar to make the profile button interactable
        final By profileParentSelector = XpathBuilder
            .from(a, withClass("p-navgroup-link--user"))
            .build();
        final WebElement profileParent = browserInteractionHelper().waitForElementToBeInteractable(profileParentSelector, pageTransitionsDuration());
        clickButton(profileParent);
        BrowserInteractionHelper.explicitWait(pageTransitionsDuration(), "first navbar click");

        clickButton(profileParent);
        BrowserInteractionHelper.explicitWait(pageTransitionsDuration(), "second navbar click");
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * For {@link XenForoHandler}, after logout we are redirected to the homepage, not the login page. While we actually ignore this page during
     * login, we need to specify it now to confirm logout.
     */
    @Override
    protected By postLogoutElementSelector() {
        return XpathBuilder
            .from(a, withClass("p-navgroup-link--logIn"))
            .build();
    }
}
