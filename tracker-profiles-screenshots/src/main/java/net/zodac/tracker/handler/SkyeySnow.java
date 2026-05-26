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
import static net.zodac.tracker.framework.xpath.HtmlElement.ul;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.atIndex;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.containsHref;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.withClass;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.withId;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import net.zodac.tracker.framework.TrackerType;
import net.zodac.tracker.framework.annotation.TrackerHandler;
import net.zodac.tracker.framework.xpath.XpathBuilder;
import net.zodac.tracker.handler.definition.NeedsExplicitTranslation;
import net.zodac.tracker.handler.redaction.RedactsIpAddress;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

/**
 * Implementation of {@link AbstractTrackerHandler} for the {@code SkyeySnow} tracker.
 */
@TrackerHandler(name = "SkyeySnow", type = TrackerType.MANUAL, url = "https://skyeysnow.com/")
public class SkyeySnow extends AbstractTrackerHandler implements NeedsExplicitTranslation, RedactsIpAddress {

    @Override
    protected By loginButtonSelector() {
        return By.name("loginsubmit");
    }

    @Override
    protected By profileLinkSelector() {
        return XpathBuilder
            .from(div, withId("um"))
            .descendant(a, containsHref("uid="))
            .build();
    }

    @Override
    protected By profilePageElementSelector() {
        return XpathBuilder
            .from(div, withClass("u_profile"))
            .build();
    }

    @Override
    public void translatePageToEnglish() {
        browserInteractionHelper().translatePage();
    }

    @Override
    public Collection<By> ipAddressElements() {
        return List.of(
            // Registration IP
            XpathBuilder
                .from(ul, withId("pbbs"))
                .child(li, atIndex(4))
                .build(),
            // Last visit IP
            XpathBuilder
                .from(ul, withId("pbbs"))
                .child(li, atIndex(5))
                .build()
        );
    }

    @Override
    protected By logoutButtonSelector() {
        openEditOutDropdownMenu();
        return XpathBuilder
            .from(ul, withId("editout_menu"))
            .child(li, atIndex(2))
            .child(a, atIndex(1))
            .build();
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * For {@link SkyeySnow}, after clicking logout the server issues a JavaScript {@code setTimeout} redirect with a
     * 2-second delay before reaching the login page. The default 500ms is insufficient for this redirect chain.
     */
    @Override
    public Duration pageTransitionsDuration() {
        return Duration.ofSeconds(5L);
    }

    private void openEditOutDropdownMenu() {
        LOGGER.debug("\t\t- Hovering over 'EDIT&LOGOUT' link to make logout button interactable");
        final By editOutSelector = By.id("editout");
        final WebElement editOutLink = driver().findElement(editOutSelector);
        browserInteractionHelper().moveTo(editOutLink);
    }
}
