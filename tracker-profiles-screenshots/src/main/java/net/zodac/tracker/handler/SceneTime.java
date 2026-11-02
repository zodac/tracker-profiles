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
import static net.zodac.tracker.framework.xpath.HtmlElement.img;
import static net.zodac.tracker.framework.xpath.HtmlElement.li;
import static net.zodac.tracker.framework.xpath.HtmlElement.table;
import static net.zodac.tracker.framework.xpath.HtmlElement.tbody;
import static net.zodac.tracker.framework.xpath.HtmlElement.td;
import static net.zodac.tracker.framework.xpath.HtmlElement.tr;
import static net.zodac.tracker.framework.xpath.HtmlElement.ul;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.atIndex;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.containsFlag;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.withAttribute;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.withClass;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.withId;

import java.util.Collection;
import java.util.List;
import net.zodac.tracker.framework.annotation.TrackerHandler;
import net.zodac.tracker.framework.xpath.XpathBuilder;
import net.zodac.tracker.handler.definition.HasDismissibleElement;
import net.zodac.tracker.handler.redaction.RedactsEmail;
import net.zodac.tracker.handler.redaction.RedactsTorrentPasskey;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

/**
 * Implementation of {@link AbstractTrackerHandler} for the {@code SceneTime} tracker.
 *
 * <p>
 * We use the login URL because the tracker sometimes redirects to the login page itself, but sometimes does not. This way we have a consistent entry
 * to the tracker.
 */
@TrackerHandler(name = "SceneTime", url = "https://scenetime.com/login.php")
public class SceneTime extends AbstractTrackerHandler implements HasDismissibleElement, RedactsEmail, RedactsTorrentPasskey {

    @Override
    protected By loginButtonSelector() {
        return XpathBuilder
            .from(form, withId("login-form"))
            .child(button, atIndex(1))
            .build();
    }

    /**
     * For {@link SceneTime}, a pop-up may appear for VIP membership, which we close before proceeding.
     */
    @Override
    public void dismiss() {
        LOGGER.debug("\t\t- Checking for promotion pop-up");
        final By promotionCloseSelector = XpathBuilder
            .from(button, withAttribute("aria-label", "Close promotion"))
            .build();
        final Collection<WebElement> announcements = driver().findElements(promotionCloseSelector);
        if (announcements.isEmpty()) {
            LOGGER.debug("\t\t\t- No promotion pop-up found");
            return;
        }

        // There should only be one of these, and we'll wait for it to scroll down and become interactable
        LOGGER.debug("\t\t\t- Found promotion pop-up, closing");
        final WebElement promotionClose = browserInteractionHelper().waitForElementToBeInteractable(promotionCloseSelector, pageLoadDuration());
        clickButton(promotionClose);

        LOGGER.debug("\t\t\t- Cleared pop-up");
    }

    @Override
    protected By profileLinkSelector() {
        return XpathBuilder
            .from(a, withClass("st-status-username"))
            .build();
    }

    @Override
    protected By profilePageElementSelector() {
        return XpathBuilder
            .from(img, containsFlag())
            .build();
    }

    @Override
    public Collection<By> emailElements() {
        return List.of(
            XpathBuilder
                .from(div, withClass("ud-main-grid"))
                .descendant(div, withClass("ud-data-list__val"))
                .build()
        );
    }

    @Override
    public Collection<By> torrentPasskeyElements() {
        return List.of(
            By.id("passkey-val")
        );
    }

    @Override
    protected By logoutButtonSelector() {
        // Highlight the user profile button to make the logout button interactable
        final By logoutParentSelector = XpathBuilder
            .from(div, withClass("st-status-profile-trigger"))
            .build();
        final WebElement logoutParent = driver().findElement(logoutParentSelector);
        clickButton(logoutParent);

        return XpathBuilder
            .from(div, withId("st-profile-menu"))
            .child(ul, atIndex(1))
            .child(li, withClass("st-pp-logout"))
            .child(a, atIndex(1))
            .build();
    }
}
