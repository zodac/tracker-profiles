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
import static net.zodac.tracker.framework.xpath.HtmlElement.li;
import static net.zodac.tracker.framework.xpath.HtmlElement.main;
import static net.zodac.tracker.framework.xpath.HtmlElement.ul;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.atIndex;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.withClass;

import java.util.Collection;
import net.zodac.tracker.framework.annotation.TrackerHandler;
import net.zodac.tracker.framework.xpath.XpathBuilder;
import net.zodac.tracker.handler.definition.HasCloudflareCheck;
import net.zodac.tracker.util.BrowserInteractionHelper;
import org.openqa.selenium.By;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebElement;

/**
 * Extension of the {@link Unit3dHandler} for the {@code SeedCore} tracker.
 */
@TrackerHandler(name = "SeedCore", url = "https://seedcore.net")
public class SeedCore extends Unit3dHandler implements HasCloudflareCheck {

    private static final By DAILY_REWARD_SELECTOR = By.id("notification-content");
    private static final int MAXIMUM_DROPDOWN_HOVER_ATTEMPTS = 3;

    @Override
    public By cloudflareSelector() {
        return XpathBuilder
            .from(div, withClass("cf-turnstile"))
            .build();
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * For {@link SeedCore} there is potentially a pop-up with a daily reward of bonus points that can be cleared. We still use
     * {@link #dismiss()} from {@link Unit3dHandler} to clear the cookie banner.
     */
    @Override
    public void dismiss() {
        super.dismiss();

        LOGGER.debug("\t\t- Checking for daily reward");
        final Collection<WebElement> dailyRewardElements = driver().findElements(DAILY_REWARD_SELECTOR);
        if (dailyRewardElements.isEmpty()) {
            LOGGER.debug("\t\t\t- No daily reward found");
            return;
        }

        // There should only be one of these, so we'll load it and click it
        LOGGER.debug("\t\t\t- Found daily reward, closing");
        final BrowserInteractionHelper browserInteractionHelper = browserInteractionHelper();
        final WebElement dailyRewardElement = browserInteractionHelper.waitForElementToBeInteractable(DAILY_REWARD_SELECTOR, pageLoadDuration());
        clickButton(dailyRewardElement);
        browserInteractionHelper.waitForElementToDisappear(DAILY_REWARD_SELECTOR, pageLoadDuration());

        LOGGER.debug("\t\t\t- Cleared daily reward");
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
     * For {@link SeedCore}, there are multiple dropdowns, and we need to select the correct one explicitly. The hover does not always open the
     * dropdown on the first try (for example, if the daily reward pop-up is still fading out), so we confirm the dropdown is visible, and if not
     * we move the cursor away and hover again, up to {@value #MAXIMUM_DROPDOWN_HOVER_ATTEMPTS} times.
     */
    @Override
    protected void openUserDropdownMenu() {
        LOGGER.debug("\t\t- Waiting for login/rules pop-up to disappear");
        final BrowserInteractionHelper browserInteractionHelper = browserInteractionHelper();
        final By loginPopupSelector = By.id("swal2-title");
        browserInteractionHelper.waitForElementToDisappear(loginPopupSelector, pageLoadDuration());
        browserInteractionHelper.waitForElementToDisappear(DAILY_REWARD_SELECTOR, pageLoadDuration());

        final By logoutParentSelector = XpathBuilder
            .from(div, withClass("top-nav__right"))
            .child(ul, atIndex(2))
            .child(li, atIndex(4))
            .build();
        final By dropdownMenuSelector = XpathBuilder
            .from(div, withClass("top-nav__right"))
            .descendant(li, withClass("top-nav__dropdown"))
            .child(ul, atIndex(1))
            .build();

        for (int attempt = 1; attempt <= MAXIMUM_DROPDOWN_HOVER_ATTEMPTS; attempt++) {
            LOGGER.debug("\t\t- Highlighting user dropdown menu to make profile/logout button interactable (attempt {}/{})", attempt,
                MAXIMUM_DROPDOWN_HOVER_ATTEMPTS);
            browserInteractionHelper.moveToOrigin(); // Move away first so a repeated hover re-triggers the dropdown
            browserInteractionHelper.moveTo(driver().findElement(logoutParentSelector));

            try {
                browserInteractionHelper.waitForElementToBeVisible(dropdownMenuSelector, pageTransitionsDuration());
                return;
            } catch (final TimeoutException e) {
                LOGGER.debug("\t\t\t- User dropdown menu not visible after hover");
                LOGGER.trace(e);
            }
        }

        LOGGER.debug("\t\t- User dropdown menu still not visible after {} attempts", MAXIMUM_DROPDOWN_HOVER_ATTEMPTS);
    }
}
