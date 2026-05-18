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
import static net.zodac.tracker.framework.xpath.HtmlElement.form;
import static net.zodac.tracker.framework.xpath.HtmlElement.img;
import static net.zodac.tracker.framework.xpath.HtmlElement.input;
import static net.zodac.tracker.framework.xpath.HtmlElement.span;
import static net.zodac.tracker.framework.xpath.HtmlElement.table;
import static net.zodac.tracker.framework.xpath.HtmlElement.tbody;
import static net.zodac.tracker.framework.xpath.HtmlElement.td;
import static net.zodac.tracker.framework.xpath.HtmlElement.tr;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.atIndex;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.containsFlag;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.withAttribute;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.withClass;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.withId;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.withType;

import java.util.Collection;
import java.util.List;
import net.zodac.tracker.framework.TrackerType;
import net.zodac.tracker.framework.annotation.CommonTrackerHandler;
import net.zodac.tracker.framework.annotation.TrackerHandler;
import net.zodac.tracker.framework.config.ApplicationConfiguration;
import net.zodac.tracker.framework.config.Configuration;
import net.zodac.tracker.framework.xpath.NamedHtmlElement;
import net.zodac.tracker.framework.xpath.XpathBuilder;
import net.zodac.tracker.handler.redaction.RedactsEmail;
import net.zodac.tracker.handler.redaction.RedactsIpAddress;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.Select;

/**
 * Common implementation of {@link AbstractTrackerHandler} for {@code NexusPHP}-based trackers.
 */
@CommonTrackerHandler("NexusPHP")
@TrackerHandler(name = "52PT", type = TrackerType.MANUAL, url = "https://52pt.site/")
@TrackerHandler(name = "HDFans", type = TrackerType.MANUAL, url = "https://hdfans.org/")
public class NexusPhpHandler extends AbstractTrackerHandler implements RedactsEmail, RedactsIpAddress {

    private static final ApplicationConfiguration CONFIG = Configuration.get();

    @Override
    protected By usernameFieldSelector() {
        // Special case where the website provides an option to use English, but only on the login page
        // We use this instead of an explicit translation to let the website handle it
        if (CONFIG.enableTranslationToEnglish()) {
            LOGGER.debug("\t\t- Selecting English as tracker language prior to login");
            final WebElement languageDropdown = driver.findElement(languageDropdownSelector());
            new Select(languageDropdown).selectByIndex(0);
            browserInteractionHelper.waitForPageToLoad(pageLoadDuration());
        }

        return super.usernameFieldSelector();
    }

    /**
     * {@link By} selector for the language drop-down menu on the login page.
     *
     * @return the {@link By} selector
     */
    protected By languageDropdownSelector() {
        return XpathBuilder
            .from(td, withId("nav_block"))
            .child(form, atIndex(1))
            .child(div, atIndex(1))
            .child(NamedHtmlElement.of("select"), atIndex(1))
            .build();
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * For {@link NexusPhpHandler}, prior to clicking the login button with a successful username/password there is another field where a
     * Captcha needs to be entered.
     *
     * <p>
     * Manual user interaction:
     * <ol>
     *     <li>Enter correct captcha value</li>
     * </ol>
     */
    @Override
    protected void preLoginClickAction() {
        LOGGER.info("\t\t >>> Waiting for user to enter captcha");

        final By captchaElementSelector = XpathBuilder
            .from(img, withAttribute("alt", "CAPTCHA"))
            .build();
        final WebElement captchaElement = driver.findElement(captchaElementSelector);
        browserInteractionHelper.highlightElement(captchaElement);
        displayUtils.confirm(trackerDefinition.name(), "Solve the captcha");
    }

    @Override
    protected By loginButtonSelector() {
        return XpathBuilder
            .from(input, withClass("btn"), withType("submit"))
            .build();
    }

    @Override
    protected By profileLinkSelector() {
        return XpathBuilder
            .from(table, withId("info_block"))
            .descendant(table, atIndex(1))
            .child(tbody, atIndex(1))
            .child(tr, atIndex(1))
            .child(td, atIndex(1))
            .child(span, atIndex(1))
            .child(span, atIndex(1))
            .child(a, atIndex(1))
            .build();
    }

    @Override
    protected By profilePageElementSelector() {
        return XpathBuilder
            .from(img, containsFlag())
            .build();
    }

    @Override
    public Collection<By> emailElements() {
        return List.of(
            XpathBuilder
                .from(table, withClass("main"))
                .descendant(table, atIndex(1))
                .child(tbody, atIndex(1))
                .child(tr)
                .child(td, atIndex(2))
                .build()
        );
    }

    @Override
    public Collection<By> ipAddressElements() {
        return List.of(
            // Current IP address
            XpathBuilder
                .from(table, withClass("main"))
                .child(tbody, atIndex(1))
                .child(tr, atIndex(1))
                .child(td, atIndex(1))
                .child(table, atIndex(1))
                .child(tbody, atIndex(1))
                .child(tr)
                .child(td, atIndex(2))
                .build(),
            // IPv4 address of current BitTorrent client
            XpathBuilder
                .from(table, withClass("main"))
                .child(tbody, atIndex(1))
                .child(tr, atIndex(1))
                .child(td, atIndex(1))
                .child(table, atIndex(1))
                .child(tbody, atIndex(1))
                .child(tr)
                .child(td, atIndex(2))
                .child(table, atIndex(1))
                .child(tbody, atIndex(1))
                .child(tr, atIndex(2))
                .child(td, atIndex(2))
                .build(),
            // IPv4 address of current BitTorrent client
            XpathBuilder
                .from(table, withClass("main"))
                .child(tbody, atIndex(1))
                .child(tr, atIndex(1))
                .child(td, atIndex(1))
                .child(table, atIndex(1))
                .child(tbody, atIndex(1))
                .child(tr)
                .child(td, atIndex(2))
                .child(table, atIndex(1))
                .child(tbody, atIndex(1))
                .child(tr, atIndex(2))
                .child(td, atIndex(3))
                .build()
        );
    }

    @Override
    protected By logoutButtonSelector() {
        return XpathBuilder
            .from(table, withId("info_block"))
            .descendant(table, atIndex(1))
            .child(tbody, atIndex(1))
            .child(tr, atIndex(1))
            .child(td, atIndex(1))
            .child(span, atIndex(1))
            .child(a, atIndex(1))
            .build();
    }

    @Override
    protected By postLogoutElementSelector() {
        return super.usernameFieldSelector();
    }
}
