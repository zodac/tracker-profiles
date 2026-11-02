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
import static net.zodac.tracker.framework.xpath.HtmlElement.span;
import static net.zodac.tracker.framework.xpath.HtmlElement.ul;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.atIndex;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.containsHref;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.withClass;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.withHref;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.withId;
import static net.zodac.tracker.framework.xpath.XpathAxis.parent;

import java.util.List;
import java.util.Map;
import net.zodac.tracker.framework.annotation.TrackerHandler;
import net.zodac.tracker.framework.xpath.NamedHtmlElement;
import net.zodac.tracker.framework.xpath.XpathBuilder;
import net.zodac.tracker.handler.definition.HasFixedHeader;
import net.zodac.tracker.handler.definition.HasFixedSidebar;
import net.zodac.tracker.handler.redaction.RedactsSensitiveInformation;
import net.zodac.tracker.util.BrowserInteractionHelper;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.remote.RemoteWebDriver;

/**
 * Implementation of {@link AbstractTrackerHandler} for the {@code SpeedApp} tracker.
 */
@TrackerHandler(name = "SpeedApp", url = "https://speedapp.io/")
public class SpeedApp extends AbstractTrackerHandler implements HasFixedHeader, HasFixedSidebar, RedactsSensitiveInformation {

    @Override
    protected By usernameFieldSelector() {
        return By.name("email");
    }

    @Override
    protected By loginButtonSelector() {
        return By.id("kt_sign_in_submit");
    }

    @Override
    protected By postLoginSelector() {
        return By.id("kt_header_user_menu_toggle");
    }

    @Override
    protected By profileLinkSelector() {
        openUserDropdownMenu();
        return XpathBuilder
            .from(div, withClass("app-user-menu"))
            .descendant(a, withHref("/profile"))
            .build();
    }

    @Override
    protected By profilePageElementSelector() {
        return XpathBuilder
            .from(span, withClass("sp-user-name"))
            .build();
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * For {@link SpeedApp}, it's simpler to delete the header than attempt to unfix it.
     */
    @Override
    public void unfixHeaders(final RemoteWebDriver driver, final List<By> headerSelectors) {
        final BrowserInteractionHelper browserInteractionHelper = BrowserInteractionHelper.of(driver);
        for (final By headerSelector : headerSelectors) {
            final WebElement headerElement = driver.findElement(headerSelector);
            browserInteractionHelper.removeElement(headerElement);
        }
    }

    @Override
    public List<By> headerSelectors() {
        return List.of(
            By.id("kt_app_header")
        );
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * For {@link SpeedApp}, it's simpler to delete the sidebar than attempt to unfix it.
     */
    @Override
    public void unfixSidebar(final RemoteWebDriver driver) {
        final WebElement sidebar = driver.findElement(By.id("kt_app_sidebar"));
        final BrowserInteractionHelper browserInteractionHelper = BrowserInteractionHelper.of(driver);
        browserInteractionHelper.removeElement(sidebar);
    }

    @Override
    public Map<String, By> sensitiveElements() {
        return Map.of(
            "IP and ISP", XpathBuilder
                .from(NamedHtmlElement.of("section"), withClass("sp-profile-hero"))
                .navigateTo(parent(div))
                .child(div, atIndex(1))
                .child(div, atIndex(2))
                .child(NamedHtmlElement.of("section"), atIndex(1))
                .child(ul, atIndex(1))
                .child(li, atIndex(1))
                .child(div, atIndex(1))
                .child(span, atIndex(1))
                .build()
        );
    }

    @Override
    protected By logoutButtonSelector() {
        reloadProfilePage(); // To bring back the header we just hid
        openUserDropdownMenu();
        return XpathBuilder
            .from(div, withId("kt_header_user_menu_toggle"))
            .descendant(a, containsHref("/logout"))
            .build();
    }

    private void openUserDropdownMenu() {
        LOGGER.debug("\t\t- Highlighting user dropdown menu to make profile/logout button interactable");
        final WebElement toggle = driver().findElement(By.id("kt_header_user_menu_toggle"));
        final BrowserInteractionHelper browserInteractionHelper = browserInteractionHelper();
        browserInteractionHelper.moveTo(toggle);
    }
}
