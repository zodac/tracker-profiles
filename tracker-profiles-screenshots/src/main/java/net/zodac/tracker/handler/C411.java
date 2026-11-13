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
import static net.zodac.tracker.framework.xpath.HtmlElement.header;
import static net.zodac.tracker.framework.xpath.HtmlElement.span;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.atIndex;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.withAttribute;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.withClass;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.withType;
import static net.zodac.tracker.framework.xpath.XpathAxis.parent;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import net.zodac.tracker.framework.annotation.TrackerHandler;
import net.zodac.tracker.framework.xpath.XpathBuilder;
import net.zodac.tracker.handler.definition.HasCloudflareCheck;
import net.zodac.tracker.handler.definition.HasDismissibleElement;
import net.zodac.tracker.handler.definition.HasFixedHeader;
import net.zodac.tracker.util.BrowserInteractionHelper;
import net.zodac.tracker.util.StringUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

/**
 * Implementation of {@link AbstractTrackerHandler} for the {@code C411} tracker.
 */
@TrackerHandler(name = "C411", url = "https://c411.org/")
public class C411 extends AbstractTrackerHandler implements HasCloudflareCheck, HasDismissibleElement, HasFixedHeader {

    /**
     * {@inheritDoc}
     *
     * <p>
     * For {@link C411}, the page is translated to English which causes a refresh, and the Cloudflare handler interprets this as a 'continue'. So
     * instead we'll wait a couple of seconds for the page to translate, then prompt the user to pass Cloudflare.
     */
    @Override
    public void preLoginNavigationAction() {
        BrowserInteractionHelper.explicitWait(Duration.ofSeconds(2L), "web page to complete translation");
    }

    @Override
    public By cloudflareSelector() {
        return XpathBuilder
            .from(div, withClass("turnstile-container"))
            .build();
    }

    @Override
    protected By loginButtonSelector() {
        return XpathBuilder
            .from(button, withType("submit"))
            .build();
    }

    @Override
    protected By postLoginSelector() {
        return By.tagName("header");
    }

    @Override
    public void dismiss() {
        // Remove the login pop-up (if it exists) since it covers the user drop down menu
        final By loginPopupsSelector = XpathBuilder
            .from(span, withClass("i-heroicons:x-mark"))
            .navigateTo(parent(button))
            .build();
        final Collection<WebElement> loginPopups = driver().findElements(loginPopupsSelector);
        LOGGER.trace("Found {} login pop-up{} to clear", loginPopups.size(), StringUtils.pluralise(loginPopups));

        for (final WebElement loginPopup : loginPopups) {
            clickButton(loginPopup);
        }
    }

    @Override
    protected By profileLinkSelector() {
        openUserDropdownMenu();
        return XpathBuilder
            .from(div, withAttribute("role", "presentation"))
            .child(div, withAttribute("role", "group"), atIndex(1))
            .child(a, atIndex(1))
            .build();
    }

    @Override
    protected By profilePageElementSelector() {
        return XpathBuilder
            .from(button)
            .child(span, atIndex(1), withClass("i-heroicons:pencil-square"))
            .build();
    }

    @Override
    public List<By> headerSelectors() {
        return List.of(XpathBuilder
            .from(header, atIndex(1))
            .build());
    }

    @Override
    protected By logoutButtonSelector() {
        openUserDropdownMenu();
        return XpathBuilder
            .from(div, withAttribute("role", "presentation"))
            .child(div, withAttribute("role", "group"), atIndex(4))
            .child(button, atIndex(1))
            .build();
    }

    private void openUserDropdownMenu() {
        LOGGER.debug("\t\t- Clicking user dropdown menu to make profile/logout button interactable");
        final By profileParentSelector = XpathBuilder
            .from(header, atIndex(1))
            .descendant(button, withAttribute("aria-haspopup", "menu"))
            .build();
        final WebElement profileParent = driver().findElement(profileParentSelector);
        clickButton(profileParent);
    }
}
