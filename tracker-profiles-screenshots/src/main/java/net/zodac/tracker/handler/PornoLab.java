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
import static net.zodac.tracker.framework.xpath.HtmlElement.table;
import static net.zodac.tracker.framework.xpath.HtmlElement.tbody;
import static net.zodac.tracker.framework.xpath.HtmlElement.td;
import static net.zodac.tracker.framework.xpath.HtmlElement.tr;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.atIndex;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.withClass;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.withName;

import net.zodac.tracker.framework.TrackerType;
import net.zodac.tracker.framework.annotation.TrackerHandler;
import net.zodac.tracker.framework.xpath.NamedHtmlElement;
import net.zodac.tracker.framework.xpath.XpathBuilder;
import net.zodac.tracker.handler.definition.HasProfilePageActions;
import net.zodac.tracker.handler.definition.NeedsExplicitTranslation;
import net.zodac.tracker.util.BrowserInteractionHelper;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

/**
 * Extension of the {@link TorrentPier} for the {@code PornoLab} tracker.
 */
@TrackerHandler(name = "PornoLab", adult = true, type = TrackerType.MANUAL, url = "https://pornolab.net/forum/tracker.php")
public class PornoLab extends TorrentPier implements HasProfilePageActions, NeedsExplicitTranslation {

    @Override
    protected By profileLinkSelector() {
        return XpathBuilder
            .from(div, withClass("topmenu"))
            .child(table, atIndex(1))
            .child(tbody, atIndex(1))
            .child(tr, atIndex(1))
            .child(td, atIndex(1))
            .child(a, atIndex(1))
            .build();
    }

    @Override
    protected By profilePageElementSelector() {
        return By.id("u_down_total");
    }

    @Override
    public void translatePageToEnglish() {
        // The default location of the cursor means a right-click won't always show the 'translate' option, so we move the cursor
        final By selector = XpathBuilder
            .from(NamedHtmlElement.of("h1"), withClass("pagetitle"))
            .build();
        browserInteractionHelper().translatePage(selector);
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * For {@link PornoLab}, we close the section of the page showing torrents currently being seeded.
     */
    @Override
    public void performActionOnProfilePage() {
        final By activeTorrentsSelector = XpathBuilder
            .from(div, withClass("active-torrents-list"))
            .child(div, atIndex(4))
            .build();

        final WebElement activeTorrents = driver().findElement(activeTorrentsSelector);
        clickButton(activeTorrents);
        BrowserInteractionHelper.explicitWait(pageTransitionsDuration(), "section to close");
    }

    @Override
    protected By logoutButtonSelector() {
        return XpathBuilder
            .from(div, withClass("topmenu"))
            .child(table, atIndex(1))
            .child(tbody, atIndex(1))
            .child(tr, atIndex(1))
            .child(td, atIndex(1))
            .child(a, atIndex(2))
            .build();
    }

    @Override
    protected By postLogoutElementSelector() {
        return XpathBuilder
            .from(div, withClass("topmenu"))
            .descendant(input, withName("login_username"))
            .build();
    }
}
