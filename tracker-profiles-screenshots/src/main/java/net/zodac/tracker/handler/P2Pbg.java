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
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.atIndex;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.containsHref;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.withClass;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import net.zodac.tracker.framework.annotation.TrackerHandler;
import net.zodac.tracker.framework.xpath.NamedHtmlElement;
import net.zodac.tracker.framework.xpath.XpathBuilder;
import net.zodac.tracker.handler.definition.HasDismissibleElement;
import net.zodac.tracker.handler.definition.RedactsEmail;
import net.zodac.tracker.handler.definition.RedactsIpAddress;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

/**
 * Implementation of {@link AbstractTrackerHandler} for the {@code P2PBG} tracker.
 */
@TrackerHandler(name = "P2PBG", url = "https://www.p2pbg.com/")
public class P2Pbg extends AbstractTrackerHandler implements HasDismissibleElement, RedactsEmail, RedactsIpAddress {

    @Override
    protected By usernameFieldSelector() {
        return By.id("want_username");
    }

    @Override
    protected By passwordFieldSelector() {
        return By.id("want_password");
    }

    @Override
    protected By loginButtonSelector() {
        return By.className("auth-button");
    }

    @Override
    public void dismiss() {
        final By cookieSelector = By.id("cookie-consent-accept");
        final Collection<WebElement> cookieButtons = driver.findElements(cookieSelector);
        LOGGER.trace("Found {} cookie banners to clear", cookieButtons.size());
        for (final WebElement cookieButton : cookieButtons) {
            clickButton(cookieButton);
        }
    }

    @Override
    protected By profileLinkSelector() {
        return XpathBuilder
            .from(a, containsHref("/usercp"))
            .build();
    }

    @Override
    protected By profilePageElementSelector() {
        return XpathBuilder
            .from(div, withClass("profile-shell__stack"))
            .child(NamedHtmlElement.of("section"), atIndex(4))
            .build();
    }

    @Override
    public Collection<By> emailElements() {
        return List.of(
            XpathBuilder
                .from(NamedHtmlElement.of("article"), withClass("profile-shell__identity-panel"), atIndex(1))
                .child(div, withClass("profile-shell__identity-entry"), atIndex(1))
                .child(div, withClass("profile-shell__identity-value"))
                .build()
        );
    }

    @Override
    public Collection<By> ipAddressElements() {
        return List.of(
            XpathBuilder
                .from(NamedHtmlElement.of("article"), withClass("profile-shell__identity-panel"), atIndex(1))
                .child(div, withClass("profile-shell__identity-entry"), atIndex(2))
                .child(div, withClass("profile-shell__identity-value"))
                .build()
        );
    }

    @Override
    protected By logoutButtonSelector() {
        return XpathBuilder
            .from(a, containsHref("/logout"))
            .build();
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * For {@link P2Pbg}, the navigation from the home page to the profile page involves a slow animated transition. The default 500ms is insufficient
     * for this transition to complete.
     */
    @Override
    public Duration pageTransitionsDuration() {
        return Duration.ofSeconds(2L);
    }
}
