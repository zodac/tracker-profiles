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
import static net.zodac.tracker.framework.xpath.HtmlElement.table;
import static net.zodac.tracker.framework.xpath.HtmlElement.td;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.atIndex;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.containsHref;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.withClass;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.withType;

import java.util.Collection;
import java.util.List;
import net.zodac.tracker.framework.TrackerType;
import net.zodac.tracker.framework.annotation.TrackerHandler;
import net.zodac.tracker.framework.xpath.XpathBuilder;
import net.zodac.tracker.handler.definition.HasProfilePageActions;
import net.zodac.tracker.handler.redaction.RedactsEmail;
import net.zodac.tracker.handler.redaction.RedactsIpAddress;
import net.zodac.tracker.handler.redaction.RedactsTorrentPasskey;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

/**
 * Implementation of {@link AbstractTrackerHandler} for the {@code Leech24} tracker.
 */
@TrackerHandler(name = "Leech24", type = TrackerType.MANUAL, url = "https://leech24.net/")
public class Leech24 extends AbstractTrackerHandler implements HasProfilePageActions, RedactsEmail, RedactsIpAddress, RedactsTorrentPasskey {

    /**
     * {@inheritDoc}
     *
     * <p>
     * For {@link Leech24}, prior to clicking the login button with a captcha that needs to be clicked and verified.
     *
     * <p>
     * Manual user interaction:
     * <ol>
     *     <li>Click and pass the captcha</li>
     * </ol>
     */
    @Override
    protected void preLoginClickAction() {
        LOGGER.info("\t\t >>> Waiting for user to solve the captcha");

        final By captchaSelector = XpathBuilder
            .from(div, withClass("g-recaptcha"))
            .child(div, atIndex(1))
            .build();
        final WebElement captchaElement = driver().findElement(captchaSelector);
        browserInteractionHelper().highlightElement(captchaElement);
        displayUtils().confirm(trackerDefinition().name(), "Solve the captcha");
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
            .from(div, withClass("myBlock-content"))
            .descendant(a, containsHref("account.php"))
            .build();
    }

    @Override
    protected By profilePageElementSelector() {
        return XpathBuilder
            .from(a, containsHref("account.php?action=changepw"))
            .build();
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * For {@link Leech24}, the profile page has one large text box with all content, making it difficult to partially redact the passkey. We use some
     * JavaScript to find the 'passkey' value then wrap it in a {@code span} HTML element.
     */
    @Override
    public void performActionOnProfilePage() {
        LOGGER.debug("\t\t- Adding <span id='passkey-value'> around the passkey value on the page");
        final String script = """
            var td = document.querySelector('.myFrame-content table.comment td');
            var bTags = td.querySelectorAll('b');
            
            var passkeyNode = bTags[11].nextSibling; // 12th <b>, 0-indexed
            var span = document.createElement('span');
            span.id = 'passkey-value';
            span.textContent = passkeyNode.textContent;
            passkeyNode.parentNode.replaceChild(span, passkeyNode);
            """;
        driver().executeScript(script);
    }

    @Override
    public Collection<By> emailElements() {
        // The email address and IP address are both contained within this single cell
        return List.of(
            XpathBuilder
                .from(div, withClass("myFrame-content"))
                .descendant(table, withClass("comment"))
                .descendant(td, atIndex(1))
                .build()
        );
    }

    @Override
    public Collection<By> ipAddressElements() {
        // The email address and IP address are both contained within this single cell
        return List.of(
            XpathBuilder
                .from(div, withClass("myFrame-content"))
                .descendant(table, withClass("comment"))
                .descendant(td, atIndex(1))
                .build()
        );
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * Uses the custom HTML element we added in {@link #performActionOnProfilePage()}.
     */
    @Override
    public Collection<By> torrentPasskeyElements() {
        return List.of(
            By.id("passkey-value")
        );
    }

    @Override
    protected By logoutButtonSelector() {
        return XpathBuilder
            .from(div, withClass("infobar"))
            .child(a, containsHref("account-logout.php"))
            .build();
    }
}
