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
import static net.zodac.tracker.framework.xpath.HtmlElement.form;
import static net.zodac.tracker.framework.xpath.HtmlElement.img;
import static net.zodac.tracker.framework.xpath.HtmlElement.input;
import static net.zodac.tracker.framework.xpath.HtmlElement.table;
import static net.zodac.tracker.framework.xpath.HtmlElement.tbody;
import static net.zodac.tracker.framework.xpath.HtmlElement.td;
import static net.zodac.tracker.framework.xpath.HtmlElement.tr;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.atIndex;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.containsSrc;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.withClass;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.withHref;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.withType;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import net.zodac.tracker.framework.annotation.TrackerHandler;
import net.zodac.tracker.framework.xpath.XpathBuilder;
import net.zodac.tracker.handler.definition.HasFixedHeader;
import net.zodac.tracker.handler.definition.HasFixedSidebar;
import net.zodac.tracker.handler.redaction.RedactsEmail;
import net.zodac.tracker.handler.redaction.RedactsIpAddress;
import net.zodac.tracker.handler.redaction.RedactsSensitiveInformation;
import net.zodac.tracker.handler.redaction.RedactsTorrentPasskey;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.remote.RemoteWebDriver;

/**
 * Implementation of {@link AbstractTrackerHandler} for the {@code Shazbat} tracker.
 */
@TrackerHandler(name = "Shazbat", url = "https://www.shazbat.tube/")
public class Shazbat extends AbstractTrackerHandler
    implements HasFixedHeader, HasFixedSidebar, RedactsEmail, RedactsIpAddress, RedactsSensitiveInformation, RedactsTorrentPasskey {

    @Override
    protected By loginButtonSelector() {
        return XpathBuilder
            .from(form, withClass("form-login"))
            .descendant(button, withType("submit"))
            .build();
    }

    @Override
    protected By postLoginSelector() {
        return XpathBuilder
            .from(div, withClass("stars-container"))
            .build();
    }

    @Override
    protected By profileLinkSelector() {
        return XpathBuilder
            .from(a, withHref("profile"))
            .build();
    }

    @Override
    protected By profilePageElementSelector() {
        return By.id("user-passkey");
    }

    @Override
    public List<By> headerSelectors() {
        return List.of(By.tagName("header"));
    }

    @Override
    public void unfixSidebar(final RemoteWebDriver driver) {
        final By toggleSelector = XpathBuilder
            .from(div, withClass("sidebar-toggle-box"))
            .child(div, atIndex(1))
            .build();
        final WebElement toggle = driver.findElement(toggleSelector);
        clickButton(toggle);

        // Move cursor to another element (the site logo) to remove the toggle pop-up
        LOGGER.trace("Moving cursor to remove pop-up");
        final By indexSelector = XpathBuilder
            .from(a, withHref("/index"))
            .build();
        final WebElement index = driver.findElement(indexSelector);
        browserInteractionHelper().moveTo(index);
    }

    @Override
    public Collection<By> ipAddressElements() {
        return List.of(
            XpathBuilder
                .from(table, withClass("table-advance"))
                .child(tbody, atIndex(1))
                .child(tr)
                .child(td, atIndex(2))
                .build()
        );
    }

    @Override
    public Collection<By> emailElements() {
        return List.of(
            XpathBuilder
                .from(input, withType("email"))
                .build()
        );
    }

    @Override
    public Collection<By> torrentPasskeyElements() {
        return List.of(
            By.id("user-passkey")
        );
    }

    @Override
    public Map<String, By> sensitiveElements() {
        return Map.of(
            "2FA QR code", XpathBuilder
                .from(img, containsSrc("data:image"))
                .build()
        );
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * For {@link Shazbat}, the tracker uses the {@code jquery.nicescroll} plugin, which replaces the native browser scrollbar with custom elements.
     * The {@link net.zodac.tracker.util.BrowserInteractionHelper#highlightElement(WebElement)} used for other trackers has no effect here. Instead,
     * we inject a {@code style} rule with {@code !important} takes precedence and keeps them hidden.
     */
    @Override
    public void actionBeforeScreenshot() {
        driver().executeScript("""
            var style = document.createElement('style');
            style.textContent = '.nicescroll-rails { display: none !important; }';
            document.head.appendChild(style);
            """);
    }

    @Override
    protected By logoutButtonSelector() {
        return XpathBuilder
            .from(button, withClass("btn-logout"))
            .build();
    }
}
