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
import static net.zodac.tracker.framework.xpath.HtmlElement.input;
import static net.zodac.tracker.framework.xpath.HtmlElement.span;
import static net.zodac.tracker.framework.xpath.HtmlElement.tbody;
import static net.zodac.tracker.framework.xpath.HtmlElement.td;
import static net.zodac.tracker.framework.xpath.HtmlElement.tr;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.atIndex;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.withClass;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.withId;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.withName;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.withType;
import static net.zodac.tracker.framework.xpath.XpathAxis.parent;

import java.util.Collection;
import java.util.List;
import net.zodac.tracker.framework.TrackerType;
import net.zodac.tracker.framework.annotation.TrackerHandler;
import net.zodac.tracker.framework.xpath.NamedHtmlElement;
import net.zodac.tracker.framework.xpath.XpathBuilder;
import net.zodac.tracker.handler.definition.HasCloudflareCheck;
import net.zodac.tracker.handler.redaction.RedactsIpAddress;
import net.zodac.tracker.handler.redaction.RedactsTorrentPasskey;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

/**
 * Implementation of {@link AbstractTrackerHandler} for the {@code HDBits} tracker.
 */
@TrackerHandler(name = "HDBits", type = TrackerType.MANUAL, url = {
    "https://hdbits.org/",
    "https://backup.hdbits.org/"
})
public class HdBits extends AbstractTrackerHandler implements HasCloudflareCheck, RedactsIpAddress, RedactsTorrentPasskey {

    @Override
    protected By usernameFieldSelector() {
        return By.name("uname");
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * For {@link HdBits}, prior to clicking the login button with a successful username/password there is another field where an image needs
     * to be selected based on a text hint.
     *
     * <p>
     * Manual user interaction:
     * <ol>
     *     <li>Select the correct image</li>
     * </ol>
     */
    @Override
    protected void preLoginClickAction() {
        final By twoFactorPasscodeSelector = XpathBuilder
            .from(input, withName("twostep_code"), withType("number"))
            .navigateTo(parent(td))
            .build();
        final WebElement twoFactorPasscodeElement = driver.findElement(twoFactorPasscodeSelector);
        browserInteractionHelper.highlightElement(twoFactorPasscodeElement);

        final By captchaSelector = XpathBuilder
            .from(div, withClass("captchaIntro"))
            .descendant(span, withClass("captchaText"))
            .build();
        final WebElement captchaTextElement = driver.findElement(captchaSelector);
        LOGGER.info("\t\t >>> Waiting for user to select the '{}' image", captchaTextElement.getText());

        final WebElement captchaElement = driver.findElement(By.id("captcha"));
        browserInteractionHelper.highlightElement(captchaElement);

        displayUtils.confirm(trackerDefinition.name(), "Select the '%s' image and 2FA (if enabled)".formatted(captchaTextElement.getText()));
    }

    @Override
    protected By loginButtonSelector() {
        return XpathBuilder
            .from(input, withType("submit"))
            .build();
    }

    @Override
    protected By profileLinkSelector() {
        return XpathBuilder
            .from(div, withClass("curuser-stats"), atIndex(1))
            .child(NamedHtmlElement.of("b"), atIndex(1))
            .child(a, atIndex(1))
            .build();
    }

    @Override
    protected By profilePageElementSelector() {
        return By.id("seclog");
    }

    @Override
    public Collection<By> ipAddressElements() {
        return List.of(
            // IP address
            XpathBuilder
                .from(td, withClass("heading"))
                .navigateTo(parent(tr))
                .child(td, atIndex(2))
                .build(),
            // IP history
            XpathBuilder
                .from(tr, withId("seclog"))
                .descendant(tr)
                .child(td, atIndex(2))
                .build()
        );
    }

    @Override
    public Collection<By> torrentPasskeyElements() {
        return List.of(
            XpathBuilder
                .from(tr, withId("seclog"))
                .navigateTo(parent(tbody))
                .child(tr, atIndex(4))
                .child(td, atIndex(2))
                .build()
        );
    }

    @Override
    protected By logoutButtonSelector() {
        return XpathBuilder
            .from(div, withClass("curuser-stats"), atIndex(1))
            .child(a, atIndex(2))
            .build();
    }
}
