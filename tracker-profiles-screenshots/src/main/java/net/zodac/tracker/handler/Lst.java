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

import static net.zodac.tracker.framework.xpath.HtmlElement.div;
import static net.zodac.tracker.framework.xpath.HtmlElement.img;
import static net.zodac.tracker.framework.xpath.HtmlElement.main;
import static net.zodac.tracker.framework.xpath.HtmlElement.span;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.withClass;

import java.util.ArrayList;
import java.util.Collection;
import net.zodac.tracker.framework.TrackerType;
import net.zodac.tracker.framework.annotation.TrackerHandler;
import net.zodac.tracker.framework.redactor.RedactionBuffer;
import net.zodac.tracker.framework.xpath.XpathBuilder;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.remote.RemoteWebDriver;

/**
 * Extension of the {@link Unit3dHandler} for the {@code LST} tracker.
 */
@TrackerHandler(name = "LST", type = TrackerType.MANUAL, url = "https://lst.gg/")
public class Lst extends Unit3dHandler {

    /**
     * {@inheritDoc}
     *
     * <p>
     * For {@link Lst}, after clicking the login button with a successful username/password, another section pops up. There is a
     * multiple-choice question, where the correct movie poster must be chosen that matches the title, and the login button pressed again.
     *
     * <p>
     * Manual user interactions:
     * <ol>
     *     <li>Select correct answer to question</li>
     *     <li>Click the login button</li>
     * </ol>
     */
    @Override
    protected void postLoginClickAction() {
        final RemoteWebDriver driver = driver();
        final String initialUrl = driver.getCurrentUrl();
        LOGGER.info("\t\t >>> Waiting for user to select correct movie poster");

        final By selectionSelector = XpathBuilder
            .from(div, withClass("auth-form__text-input-group"))
            .build();
        final WebElement selectionElement = driver.findElement(selectionSelector);
        browserInteractionHelper().highlightElement(selectionElement);
        displayUtils().confirm(trackerDefinition().name(), "Select the correct movie");

        // If the user didn't click 'login', do it for them
        // Special case for PTP since the login button needs to be clicked twice
        final String nextUrl = driver.getCurrentUrl();
        if (nextUrl.equalsIgnoreCase(initialUrl)) {
            final By loginButtonSelector = loginButtonSelector();
            if (loginButtonSelector != null) {
                final WebElement loginButton = driver.findElement(loginButtonSelector);
                clickButton(loginButton);
            }
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * For {@link Lst}, unlike most other {@code UNIT3D}-based trackers, there is no link to the username available in the nav bar. Instead, we
     * must move the mouse over the profile icon in the nav bar. This will activate the dropdown menu and make the profile link button interactive.
     */
    @Override
    protected By profileLinkSelector() {
        openUserDropdownMenu();
        return XpathBuilder
            .from(img, withClass("top-nav__profile-image"))
            .build();
    }

    @Override
    protected By profilePageElementSelector() {
        return XpathBuilder
            .from(main, withClass("page__user-profile--show"))
            .build();
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * For {@link Lst}, the email is actually defined in a separate section, unlike other {@code UNIT3D}-based trackers.
     */
    @Override
    public Collection<By> emailElements() {
        final Collection<By> allEmailElements = new ArrayList<>(super.emailElements());

        allEmailElements.add(
            XpathBuilder
                .from(span, withClass("profile-hero__account-value--email"))
                .build()
        );

        return allEmailElements;
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * The {@link RedactionBuffer} set in {@link Unit3dHandler} is excessive for {@link Lst}, so we use {@link RedactionBuffer#DEFAULT} instead.
     */
    @Override
    public RedactionBuffer emailElementBuffer() {
        return RedactionBuffer.DEFAULT;
    }
}
