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
import static net.zodac.tracker.framework.xpath.HtmlElement.li;
import static net.zodac.tracker.framework.xpath.HtmlElement.ul;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.atIndex;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.withClass;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.withId;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.withName;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.withType;

import java.util.Collection;
import java.util.List;
import net.zodac.tracker.framework.annotation.CommonTrackerHandler;
import net.zodac.tracker.framework.annotation.TrackerHandler;
import net.zodac.tracker.framework.xpath.XpathBuilder;
import net.zodac.tracker.handler.redaction.RedactsEmail;
import net.zodac.tracker.handler.redaction.RedactsIpAddress;
import org.jspecify.annotations.Nullable;
import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebElement;

/**
 * Common implementation of {@link AbstractTrackerHandler} for {@code Gazelle}-based trackers.
 */
@CommonTrackerHandler("Gazelle")
@TrackerHandler(name = "SecretCinema", url = "https://secret-cinema.pw/")
public class GazelleHandler extends AbstractTrackerHandler implements RedactsEmail, RedactsIpAddress {

    @Nullable
    @Override
    protected By loginPageSelector() {
        return XpathBuilder
            .from(div, withId("logo"))
            .child(ul, atIndex(1))
            .child(li, atIndex(2))
            .child(a, atIndex(1))
            .build();
    }

    @Override
    protected By loginButtonSelector() {
        return XpathBuilder
            .from(input, withName("login"), withType("submit"))
            .build();
    }

    @Override
    protected By profilePageElementSelector() {
        return XpathBuilder
            .from(div, withClass("box_userinfo_stats"))
            .build();
    }

    /**
     * For some implementations of {@link GazelleHandler}, if the user has linked their {@code Last.fm} account there is an element that loads the
     * last-played song, and this can be slower to load than the rest of the page. So we check for the existence of the {@code Last.fm} username
     * (which is always loaded), and if it exists we wait for the 'Last played' value to load. This can sometimes not load at all, so we will wait for
     * the element here before returning. If it doesn't load, we will log a warning but continue execution.
     *
     * <p>
     * If there is no {@code Last.fm} username, we use the default {@link #profilePageElementSelector()}.
     */
    protected By profilePageElementSelectorWithLastFm() {
        try {
            LOGGER.debug("\t\t- Looking for Last.fm section for user");
            final By lastFmSectionSelector = XpathBuilder
                .from(div, withClass("box_lastfm"))
                .build();
            driver.findElement(lastFmSectionSelector);

            LOGGER.debug("\t\t- Found Last.fm section, waiting for 'last played' entry to load");
            final By lastFmLastPlayedSelector = By.id("lastfm_stats");
            browserInteractionHelper.waitForElementToBeVisible(lastFmLastPlayedSelector, pageLoadDuration());
            return lastFmLastPlayedSelector;
        } catch (final NoSuchElementException e) {
            LOGGER.debug("\t\t- Found no Last.fm section, assuming user has not linked account, will use fallback item to confirm profile page", e);
            return profilePageElementSelector();
        } catch (final TimeoutException e) {
            LOGGER.warn("\t\t- Unable to find Last.fm 'last played' section");
            throw e;
        }
    }

    @Override
    public Collection<By> emailElements() {
        return List.of(
            XpathBuilder
                .from(ul, withClass("stats"))
                .child(li)
                .child(a)
                .build()
        );
    }

    @Override
    public Collection<By> ipAddressElements() {
        return List.of(
            // Last connected IP address
            XpathBuilder
                .from(div, withId("footer"))
                .descendant(a)
                .build(),
            // IP address in profile sidebar
            By.id("statuscont0")
        );
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * For {@link GazelleHandler}-based trackers, sometimes the logout button is not visible until the link to the user's profile is hovered over.
     * This is not required for all trackers, but since it is a simple mouse move, we can execute it for all trackers and then return the appropriate
     * {@link By} selector.
     */
    @Override
    protected By logoutButtonSelector() {
        // Highlight the profile menu to make the logout button interactable
        final WebElement logoutParent = driver.findElement(profileLinkSelector());
        browserInteractionHelper.moveTo(logoutParent);

        return XpathBuilder
            .from(li, withId("nav_logout"))
            .child(a, atIndex(1))
            .build();
    }
}
