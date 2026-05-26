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
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.atIndex;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.withAttribute;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.withClass;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.withType;

import java.util.Collection;
import net.zodac.tracker.framework.TrackerType;
import net.zodac.tracker.framework.annotation.TrackerHandler;
import net.zodac.tracker.framework.xpath.XpathBuilder;
import net.zodac.tracker.handler.definition.HasCloudflareCheck;
import net.zodac.tracker.handler.definition.HasProfilePageActions;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.remote.RemoteWebDriver;

/**
 * Implementation of {@link AbstractTrackerHandler} for the {@code Hawke-Uno} tracker.
 */
@TrackerHandler(name = "Hawke-Uno", type = TrackerType.MANUAL, url = "https://hawke.uno/")
public class HawkeUno extends AbstractTrackerHandler implements HasCloudflareCheck, HasProfilePageActions {

    @Override
    protected By loginButtonSelector() {
        return XpathBuilder
            .from(button, withType("submit"))
            .build();
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * For {@link HawkeUno}, there is a mandatory 2FA check which must be completed.
     */
    @Override
    protected void postLoginClickAction() {
        final RemoteWebDriver driver = driver();
        final String initialUrl = driver.getCurrentUrl();
        LOGGER.info("\t\t >>> Waiting for user to enter 2FA code");

        final By twoFactorSelector = XpathBuilder
            .from(div, withClass("ds-auth__card"))
            .child(form, atIndex(1))
            .build();
        final WebElement twoFactorElement = driver.findElement(twoFactorSelector);
        browserInteractionHelper().highlightElement(twoFactorElement);
        displayUtils().confirm(trackerDefinition().name(), "Enter the 2FA code");

        // If the user didn't click 'verify', do it for them
        final String nextUrl = driver.getCurrentUrl();
        if (nextUrl.equalsIgnoreCase(initialUrl)) {
            final By verifyTwoFactorButtonSelector = XpathBuilder
                .from(button, withType("submit"))
                .build();
            final WebElement verifyButton = driver.findElement(verifyTwoFactorButtonSelector);
            clickButton(verifyButton);
        }
    }

    @Override
    protected By profileLinkSelector() {
        return XpathBuilder
            .from(div, withClass("ds-user-stats"))
            .child(div, atIndex(1))
            .child(a, atIndex(1))
            .build();
    }

    @Override
    protected By profilePageElementSelector() {
        return XpathBuilder
            .from(div, withClass("deep-space-user-hub"))
            .build();
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * For {@link HawkeUno}, there is no user details page in the 'hub', but there is a dropdown menu which exposes the stats.
     */
    @Override
    public void performActionOnProfilePage() {
        openUserDropdownMenu();
    }

    @Override
    protected By logoutButtonSelector() {
        openUserDropdownMenu();
        return XpathBuilder
            .from(button, withAttribute("title", "Sign Out"), withType("button"))
            .build();
    }

    private void openUserDropdownMenu() {
        final RemoteWebDriver driver = driver();
        final By signOutButtonSelector = XpathBuilder
            .from(button, withAttribute("title", "Sign Out"), withType("button"))
            .build();
        final Collection<WebElement> signOutButtons = driver.findElements(signOutButtonSelector);
        final boolean dropdownAlreadyOpen = signOutButtons.stream().anyMatch(WebElement::isDisplayed);

        if (dropdownAlreadyOpen) {
            LOGGER.debug("\t\t- User dropdown already open, skipping toggle");
            return;
        }

        LOGGER.debug("\t\t- Clicking user details toggle");
        final By userDetailsToggleSelector = XpathBuilder
            .from(button, withAttribute("title", "Toggle details"), withType("button"))
            .build();
        final WebElement userDetailsToggleElement = driver.findElement(userDetailsToggleSelector);
        clickButton(userDetailsToggleElement);
    }
}
