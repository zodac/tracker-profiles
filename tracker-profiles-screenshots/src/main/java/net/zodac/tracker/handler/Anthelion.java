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
import static net.zodac.tracker.framework.xpath.HtmlElement.img;
import static net.zodac.tracker.framework.xpath.HtmlElement.input;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.atIndex;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.withId;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.withName;
import static net.zodac.tracker.framework.xpath.XpathAxis.parent;

import net.zodac.tracker.framework.TrackerType;
import net.zodac.tracker.framework.annotation.TrackerHandler;
import net.zodac.tracker.framework.xpath.XpathBuilder;
import org.jspecify.annotations.Nullable;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

/**
 * Extension of the {@link GazelleHandler} for the {@code Anthelion} tracker.
 */
@TrackerHandler(name = "Anthelion", type = TrackerType.MANUAL, url = "https://anthelion.me/")
public class Anthelion extends GazelleHandler {

    @Override
    @Nullable
    protected By loginPageSelector() {
        return null;
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * For {@link Anthelion} 2FA is now mandatory, so must be entered by the user.
     *
     * <p>
     * Manual user interaction:
     * <ol>
     *     <li>Enter the 2FA code</li>
     * </ol>
     */
    @Override
    protected void preLoginClickAction() {
        LOGGER.info("\t\t >>> Waiting for user to enter their 2FA code");

        final By twoFaSelector = By.id("twofa");
        final WebElement twoFaElement = driver().findElement(twoFaSelector);
        browserInteractionHelper().highlightElement(twoFaElement);
        displayUtils().confirm(trackerDefinition().name(), "Enter the 2FA code");
    }

    @Override
    protected By logoutButtonSelector() {
        // Highlight the nav bar to make the logout button interactable
        final By logoutParentSelector = By.id("nav_user");
        final WebElement logoutParent = driver().findElement(logoutParentSelector);
        browserInteractionHelper().moveTo(logoutParent);

        return XpathBuilder
            .from(div, withId("user_menu"))
            .child(a, atIndex(2))
            .build();
    }
}
