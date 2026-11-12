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
import static net.zodac.tracker.framework.xpath.HtmlElement.li;
import static net.zodac.tracker.framework.xpath.HtmlElement.ul;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.atIndex;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.withClass;

import java.util.Collection;
import java.util.List;
import net.zodac.tracker.framework.TrackerType;
import net.zodac.tracker.framework.annotation.TrackerHandler;
import net.zodac.tracker.framework.xpath.XpathBuilder;
import net.zodac.tracker.handler.definition.HasCloudflareCheck;
import net.zodac.tracker.handler.definition.HasDismissibleElement;
import net.zodac.tracker.util.StringUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.ElementNotInteractableException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.remote.RemoteWebDriver;

/**
 * Extension of the {@link LuminanceHandler} for the {@code CGPeers} tracker.
 */
@TrackerHandler(name = "CGPeers", type = TrackerType.MANUAL, url = {
    "https://cgpeers.to/",
    "https://cgpeers.com/"
})
public class CgPeers extends LuminanceHandler implements HasCloudflareCheck, HasDismissibleElement {

    @Override
    public By loginPageSelector() {
        return XpathBuilder
            .from(ul, withClass("nav-list"))
            .child(li, atIndex(1))
            .child(a, atIndex(1))
            .build();
    }

    @Override
    public By cloudflareSelector() {
        return XpathBuilder
            .from(div, withClass("cf-turnstile"))
            .child(div, atIndex(1))
            .build();
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * For {@link CgPeers}, after clicking the login button with a successful username/password, an 2FA screen appears requesting a code
     * that was sent to your email.
     *
     * <p>
     * Manual user interactions:
     * <ol>
     *     <li>Enter the emailed 2FA code</li>
     *     <li>Click the 'Verify Code' button</li>
     * </ol>
     */
    @Override
    protected void postLoginClickAction() {
        final RemoteWebDriver driver = driver();
        final String initialUrl = driver.getCurrentUrl();
        LOGGER.info("\t\t >>> Waiting for user to enter the 2FA code and click the 'Verify Code' button");

        final By selectionSelector = By.name("code");
        final WebElement selectionElement = driver.findElement(selectionSelector);
        browserInteractionHelper().highlightElement(selectionElement);
        displayUtils().confirm(trackerDefinition().name(), "Enter the 2FA code");

        // If the user didn't click 'Verify Code', do it for them (it shares the same HTML ID as the login button from the previous page)
        final String nextUrl = driver.getCurrentUrl();
        if (nextUrl.equalsIgnoreCase(initialUrl)) {
            final By loginButtonSelector = loginButtonSelector();
            if (loginButtonSelector != null) {
                final WebElement loginButton = driver.findElement(loginButtonSelector);
                clickButton(loginButton);
            }
        }
    }

    @Override
    protected By postLoginSelector() {
        return By.id("userDropdownTrigger");
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * For {@link CgPeers}, two {@code announcement-dismiss} buttons are present in the DOM simultaneously, cycling between hidden and visible
     * states. We attempt to close both, but only on a best-effort basis.
     */
    @Override
    public void dismiss() {
        LOGGER.debug("\t\t- Checking for 2FA announcements");
        final By announcementSelector = XpathBuilder
            .from(button, withClass("announcement-dismiss"))
            .build();
        final Collection<WebElement> announcements = driver().findElements(announcementSelector);

        if (announcements.isEmpty()) {
            LOGGER.debug("\t\t\t- No announcements found");
            return;
        }

        LOGGER.debug("\t\t\t- Found {} announcement{}, clearing", announcements.size(), StringUtils.pluralise(announcements));
        for (final WebElement announcement : announcements) {
            try {
                clickButton(announcement);
                browserInteractionHelper().waitForElementToDisappear(announcementSelector, pageTransitionsDuration());
            } catch (final ElementNotInteractableException e) {
                LOGGER.trace("Element not interactable", e);
                LOGGER.debug("\t\t\t- Element not interactable");
            }
        }

        LOGGER.debug("\t\t- Cleared announcements");
    }

    @Override
    protected By profileLinkSelector() {
        openUserDropdownMenu();
        return XpathBuilder
            .from(div, withClass("dropdown-quick-actions-grid"))
            .child(a, atIndex(1))
            .build();
    }

    @Override
    protected By profilePageElementSelector() {
        return By.id("profileShell");
    }

    @Override
    public Collection<By> emailElements() {
        return List.of();
    }

    @Override
    public Collection<By> ipAddressElements() {
        return List.of();
    }

    @Override
    public Collection<By> torrentPasskeyElements() {
        return List.of();
    }

    @Override
    protected By logoutButtonSelector() {
        openUserDropdownMenu();
        return XpathBuilder
            .from(div, withClass("dropdown-quick-actions-grid"))
            .child(a, atIndex(6))
            .build();
    }

    private void openUserDropdownMenu() {
        LOGGER.debug("\t\t- Clicking user dropdown menu to make profile/logout button interactable");
        final By profileParentSelector = By.id("userDropdownTrigger");
        final WebElement profileParent = driver().findElement(profileParentSelector);
        clickButton(profileParent);
    }
}
