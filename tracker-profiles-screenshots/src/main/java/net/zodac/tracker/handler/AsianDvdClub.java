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
import static net.zodac.tracker.framework.xpath.HtmlElement.img;
import static net.zodac.tracker.framework.xpath.HtmlElement.input;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.containsHref;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.withClass;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.withText;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.withType;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import net.zodac.tracker.framework.TrackerType;
import net.zodac.tracker.framework.annotation.TrackerHandler;
import net.zodac.tracker.framework.xpath.XpathBuilder;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

/**
 * Implementation of {@link AbstractTrackerHandler} for the {@code AsianDvdClub} tracker.
 */
@TrackerHandler(name = "AsianDVDClub", type = TrackerType.MANUAL, url = "https://asiandvdclub.org/")
public class AsianDvdClub extends AbstractTrackerHandler {

    /**
     * {@inheritDoc}
     *
     * <p>
     * For {@link AsianDvdClub}, when loading the page the site can request you to wait for 2 minutes before the login page is allowed to load.
     */
    @Override
    protected void preLoginNavigationAction() {
        final By progressBarSelector = By.id("progressBar");
        final Collection<WebElement> progressBars = driver().findElements(progressBarSelector);
        if (progressBars.isEmpty()) {
            LOGGER.trace("No progress bar found prior to login");
            return;
        }

        LOGGER.info("\t\t- Progress bar found, assuming we need to wait for 2 minutes");

        final By skipToLoginSelector = XpathBuilder
            .from(a, withText("Skip to Login"))
            .build();
        final List<WebElement> skipToLoginElements = driver().findElements(skipToLoginSelector);
        if (!progressBars.isEmpty()) {
            LOGGER.trace("Found a button to skip to login, trying to click it");
            clickButton(skipToLoginElements.getFirst());
        }

        browserInteractionHelper().waitForElementToBeInteractable(loginButtonSelector(), Duration.ofMinutes(2L));
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * For {@link AsianDvdClub}, you may be asked to enter some personal account information before being able to log in.
     *
     * <p>
     * Manual user interaction:
     * <ol>
     *     <li>Enter personal information</li>
     * </ol>
     */
    @Override
    protected void preLoginClickAction() {
        LOGGER.info("\t\t >>> Waiting for user to enter their personal information");

        final By birthdaySelector = By.id("birthday");
        final WebElement birthdayElement = driver().findElement(birthdaySelector);
        browserInteractionHelper().highlightElement(birthdayElement);
        displayUtils().confirm(trackerDefinition().name(), "Enter the requested information");
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
            .from(a, containsHref("/profile"))
            .build();
    }

    @Override
    protected By profilePageElementSelector() {
        return XpathBuilder
            .from(img, withClass("profile-avatar"))
            .build();
    }

    @Override
    protected By logoutButtonSelector() {
        return XpathBuilder
            .from(a, containsHref("/logout"))
            .build();
    }

    @Override
    protected By postLogoutElementSelector() {
        return loginButtonSelector();
    }
}
