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
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.withClass;

import java.util.Collection;
import java.util.List;
import net.zodac.tracker.framework.annotation.TrackerHandler;
import net.zodac.tracker.framework.xpath.XpathBuilder;
import net.zodac.tracker.util.BrowserInteractionHelper;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

/**
 * Extension of the {@link Unit3dHandler} for the {@code Podzemlje} tracker.
 */
@TrackerHandler(name = "Podzemlje", url = "https://podzemlje.net/")
public class Podzemlje extends Unit3dHandler {

    @Override
    protected By profilePageElementSelector() {
        return By.id("profile-overview");
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * For {@link Podzemlje}, the email address is hidden by default.
     */
    @Override
    public Collection<By> emailElements() {
        return List.of();
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * For {@link Podzemlje}, the user dropdown menu needs to be clicked, not just hovered over.
     */
    @Override
    protected void openUserDropdownMenu() {
        LOGGER.debug("\t\t- Waiting for login/rules pop-up to disappear");
        final BrowserInteractionHelper browserInteractionHelper = browserInteractionHelper();
        final By loginPopupSelector = By.id("swal2-title");
        browserInteractionHelper.waitForElementToDisappear(loginPopupSelector, pageLoadDuration());

        LOGGER.debug("\t\t- Clicking user dropdown menu to make profile/logout button interactable");
        final By profileParentSelector = XpathBuilder
            .from(div, withClass("top-nav__right"))
            .descendant(li, withClass("top-nav__dropdown"))
            .build();
        final WebElement profileParent = driver().findElement(profileParentSelector);
        browserInteractionHelper.moveTo(profileParent);
        clickButton(profileParent);
    }
}
