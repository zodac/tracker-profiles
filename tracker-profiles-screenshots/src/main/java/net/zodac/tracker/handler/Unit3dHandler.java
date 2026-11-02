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
import static net.zodac.tracker.framework.xpath.HtmlElement.li;
import static net.zodac.tracker.framework.xpath.HtmlElement.main;
import static net.zodac.tracker.framework.xpath.HtmlElement.table;
import static net.zodac.tracker.framework.xpath.HtmlElement.tbody;
import static net.zodac.tracker.framework.xpath.HtmlElement.td;
import static net.zodac.tracker.framework.xpath.HtmlElement.tr;
import static net.zodac.tracker.framework.xpath.HtmlElement.ul;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.atIndex;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.withClass;

import java.util.Collection;
import java.util.List;
import net.zodac.tracker.framework.annotation.CommonTrackerHandler;
import net.zodac.tracker.framework.annotation.TrackerHandler;
import net.zodac.tracker.framework.redactor.RedactionBuffer;
import net.zodac.tracker.framework.xpath.NamedHtmlElement;
import net.zodac.tracker.framework.xpath.XpathBuilder;
import net.zodac.tracker.handler.definition.HasDismissibleElement;
import net.zodac.tracker.handler.definition.HasFixedHeader;
import net.zodac.tracker.handler.redaction.RedactsEmail;
import net.zodac.tracker.handler.redaction.RedactsIpAddress;
import net.zodac.tracker.util.BrowserInteractionHelper;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;


/**
 * Common implementation of {@link AbstractTrackerHandler} for {@code UNIT3D}-based trackers.
 */
@CommonTrackerHandler("UNIT3D")
@TrackerHandler(name = "ArabicSource", url = "https://arabicsource.net/")
@TrackerHandler(name = "AsianCinema", url = "https://eiga.moi/")
@TrackerHandler(name = "Blutopia", url = "https://blutopia.cc/")
@TrackerHandler(name = "Concertos", url = "https://concertos.live/")
@TrackerHandler(name = "DesiTorrents", url = "https://desitorrents.tv/")
@TrackerHandler(name = "F1Carreras", url = "https://f1carreras.xyz/")
@TrackerHandler(name = "HDUnited", url = "https://hd-united.vn/")
@TrackerHandler(name = "Hellenic-HD", url = "https://hellenic-hd.cc/")
@TrackerHandler(name = "ItaTorrents", url = "https://itatorrents.xyz/")
@TrackerHandler(name = "LDU", url = "https://theldu.to/")
@TrackerHandler(name = "Luminarr", url = "https://luminarr.me/")
@TrackerHandler(name = "MidnightScene", url = "https://midnightscene.cc/")
@TrackerHandler(name = "Milnueve", url = "https://milnueve.cc/")
@TrackerHandler(name = "NordicQuality", url = "https://nordicq.org/")
@TrackerHandler(name = "OldToons.World", url = "https://oldtoons.world/")
@TrackerHandler(name = "OnlyEncodes", url = "https://onlyencodes.cc/")
@TrackerHandler(name = "Racing4Everyone", url = "https://racing4everyone.eu/")
@TrackerHandler(name = "Rastastugan", url = "https://rastastugan.org/")
@TrackerHandler(name = "RocketHD", url = "https://rocket-hd.cc/")
@TrackerHandler(name = "SexTorrent", adult = true, url = "https://sextorrent.myds.me/")
@TrackerHandler(name = "Unwalled", url = "https://unwalled.cc/")
@TrackerHandler(name = "VietMediaF", url = "https://tracker.vietmediaf.store/")
public class Unit3dHandler extends AbstractTrackerHandler implements HasDismissibleElement, HasFixedHeader, RedactsEmail, RedactsIpAddress {

    @Override
    protected By loginButtonSelector() {
        return XpathBuilder
            .from(button, withClass("auth-form__primary-button"))
            .build();
    }

    @Override
    protected By postLoginSelector() {
        return XpathBuilder
            .from(ul, withClass("top-nav__ratio-bar"))
            .build();
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * For many {@link Unit3dHandler}-based trackers, there is a cookie banner on first log-in. We'll search for this and click it to clear if any
     * exist.
     */
    @Override
    public void dismiss() {
        final By cookieSelector = XpathBuilder
            .from(button, withClass("cookie-consent__agree"))
            .build();

        final Collection<WebElement> cookieButtons = driver().findElements(cookieSelector);
        LOGGER.trace("Found {} cookie banners to clear", cookieButtons.size());
        for (final WebElement cookieButton : cookieButtons) {
            clickButton(cookieButton);
        }

        // Move the mouse, or else a dropdown menu is highlighted and covers some of the page
        browserInteractionHelper().moveToOrigin();
    }

    @Override
    protected By profileLinkSelector() {
        openUserDropdownMenu();
        return XpathBuilder
            .from(div, withClass("top-nav__right"))
            .descendant(li, withClass("top-nav__dropdown"))
            .child(ul, atIndex(1))
            .child(li, atIndex(1))
            .child(a, atIndex(1))
            .build();
    }

    @Override
    protected By profilePageElementSelector() {
        return XpathBuilder
            .from(main)
            .child(NamedHtmlElement.of("article"), withClass("sidebar2"))
            .build();
    }

    @Override
    public List<By> headerSelectors() {
        return List.of(By.tagName("header"));
    }

    @Override
    public Collection<By> emailElements() {
        return List.of(
            XpathBuilder
                .from(div, withClass("key-value__group"))
                .child(NamedHtmlElement.of("dd"))
                .build()
        );
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * The redaction doesn't cover the full {@code <li>} element for some reason, so we extend it to the left.
     */
    @Override
    public RedactionBuffer emailElementBuffer() {
        return RedactionBuffer.withLeftOffset(6);
    }

    @Override
    public Collection<By> ipAddressElements() {
        return List.of(
            XpathBuilder
                .from(table, withClass("data-table"))
                .child(tbody)
                .child(tr)
                .child(td, atIndex(2))
                .build()
        );
    }

    @Override
    protected By logoutButtonSelector() {
        openUserDropdownMenu();
        return XpathBuilder
            .from(div, withClass("top-nav__right"))
            .descendant(li, withClass("top-nav__dropdown"))
            .descendant(form, atIndex(1))
            .descendant(button, atIndex(1))
            .build();
    }

    /**
     * Opens the user's dropdown menu to expose links to the user profile and the logout button.
     */
    protected void openUserDropdownMenu() {
        LOGGER.debug("\t\t- Waiting for login/rules pop-up to disappear");
        final BrowserInteractionHelper browserInteractionHelper = browserInteractionHelper();
        final By loginPopupSelector = By.id("swal2-title");
        browserInteractionHelper.waitForElementToDisappear(loginPopupSelector, pageLoadDuration());

        LOGGER.debug("\t\t- Highlighting user dropdown menu to make profile/logout button interactable");
        final By profileParentSelector = XpathBuilder
            .from(div, withClass("top-nav__right"))
            .descendant(li, withClass("top-nav__dropdown"))
            .build();
        final WebElement profileParent = driver().findElement(profileParentSelector);
        browserInteractionHelper.moveTo(profileParent);
    }
}
