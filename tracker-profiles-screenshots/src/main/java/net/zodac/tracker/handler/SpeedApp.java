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
import static net.zodac.tracker.framework.xpath.HtmlElement.code;
import static net.zodac.tracker.framework.xpath.HtmlElement.div;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.atIndex;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.containsHref;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.withClass;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.withHref;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.withId;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.withNumberOfChildrenOfType;
import static net.zodac.tracker.framework.xpath.XpathAxis.followingSibling;

import java.util.Collection;
import java.util.List;
import net.zodac.tracker.framework.annotation.TrackerHandler;
import net.zodac.tracker.framework.xpath.NamedHtmlElement;
import net.zodac.tracker.framework.xpath.XpathBuilder;
import net.zodac.tracker.handler.definition.HasFixedHeader;
import net.zodac.tracker.handler.definition.HasFixedSidebar;
import net.zodac.tracker.handler.redaction.RedactsEmail;
import net.zodac.tracker.handler.redaction.RedactsIpAddress;
import net.zodac.tracker.handler.redaction.RedactsTorrentPasskey;
import net.zodac.tracker.util.BrowserInteractionHelper;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.remote.RemoteWebDriver;

/**
 * Implementation of {@link AbstractTrackerHandler} for the {@code SpeedApp} tracker.
 */
@TrackerHandler(name = "SpeedApp", url = "https://speedapp.io/")
public class SpeedApp extends AbstractTrackerHandler
    implements HasFixedHeader, HasFixedSidebar, RedactsEmail, RedactsIpAddress, RedactsTorrentPasskey {

    @Override
    protected By loginButtonSelector() {
        return XpathBuilder
            .from(button, withClass("btn-light-primary"))
            .build();
    }

    @Override
    protected By postLoginSelector() {
        return By.id("kt_quick_user_toggle");
    }

    @Override
    protected By profileLinkSelector() {
        openUserDropdownMenu();
        return XpathBuilder
            .from(div, withId("kt_quick_user"))
            .descendant(a, withHref("/profile"))
            .build();
    }

    @Override
    protected By profilePageElementSelector() {
        return By.id("kt_profile_aside");
    }

    @Override
    public List<By> headerSelectors() {
        return List.of(
            By.cssSelector("#kt_header"),
            By.cssSelector("#kt_subheader")
        );
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * For {@link SpeedApp}, the sidebar is collapsed by clicking a toggle button. The collapsed state persists across page reloads (stored as the
     * {@code aside-minimize} CSS class on the {@code <body>} element). This way the toggle is only clicked if the sidebar is not already collapsed,
     * to avoid re-opening it on a second attempt or a page refresh.
     *
     * <p>
     * Even when collapsed, the sidebar element retains {@code position: fixed} via the {@code .aside-fixed .aside} CSS rule applied through
     * the {@code <body>} class. So after collapsing, the sidebar element itself is also explicitly unfixed.
     */
    @Override
    public void unfixSidebar(final RemoteWebDriver driver) {
        final WebElement body = driver.findElement(By.tagName("body"));
        final String bodyClass = body.getAttribute("class");
        if (bodyClass == null || !bodyClass.contains("aside-minimize")) {
            final WebElement toggle = driver.findElement(By.id("kt_aside_toggle"));
            clickButton(toggle);
        }
        final BrowserInteractionHelper helper = BrowserInteractionHelper.of(driver);
        final WebElement aside = driver.findElement(By.id("kt_aside"));
        helper.makeUnfixed(aside);
    }

    @Override
    public Collection<By> emailElements() {
        return List.of(
            XpathBuilder
                .from(NamedHtmlElement.of("dl"), withClass("row"))
                .child(NamedHtmlElement.of("dd"), withNumberOfChildrenOfType(code, 1))
                .navigateTo(followingSibling(NamedHtmlElement.of("dd"), atIndex(1)))
                .build()
        );
    }

    @Override
    public Collection<By> ipAddressElements() {
        return List.of(
            XpathBuilder
                .from(NamedHtmlElement.of("dl"), withClass("row"))
                .child(NamedHtmlElement.of("dd"))
                .child(NamedHtmlElement.of("dl"))
                .descendant(div, withClass("text-success"))
                .build()
        );
    }

    @Override
    public Collection<By> torrentPasskeyElements() {
        return List.of(
            XpathBuilder
                .from(NamedHtmlElement.of("dl"), withClass("row"))
                .child(NamedHtmlElement.of("dd"))
                .child(code)
                .build()
        );
    }

    @Override
    protected By logoutButtonSelector() {
        openUserDropdownMenu();
        return XpathBuilder
            .from(div, withId("kt_quick_user"))
            .descendant(a, containsHref("/logout"))
            .build();
    }

    private void openUserDropdownMenu() {
        LOGGER.debug("\t\t- Clicking user dropdown to make profile/logout button interactable");
        final WebElement toggle = driver().findElement(By.id("kt_quick_user_toggle"));
        clickButton(toggle);
    }
}
