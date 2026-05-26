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
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.containsAttribute;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.withAttribute;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.withClass;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.withId;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.withType;

import java.util.List;
import net.zodac.tracker.framework.TrackerType;
import net.zodac.tracker.framework.annotation.TrackerHandler;
import net.zodac.tracker.framework.xpath.XpathBuilder;
import net.zodac.tracker.handler.definition.HasFixedHeader;
import net.zodac.tracker.handler.definition.HasFixedSidebar;
import net.zodac.tracker.handler.definition.NeedsExplicitTranslation;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.remote.RemoteWebDriver;

/**
 * Extension of {@link AbstractTrackerHandler} for the {@code Nexum-Core} tracker.
 */
@TrackerHandler(name = "Nexum-Core", type = TrackerType.MANUAL, url = "https://nexum-core.com/")
public class NexumCore extends AbstractTrackerHandler implements HasFixedHeader, HasFixedSidebar, NeedsExplicitTranslation {

    @Override
    protected By loginButtonSelector() {
        return XpathBuilder
            .from(button, withClass("btn-primary"), withType("submit"))
            .build();
    }

    @Override
    protected By profileLinkSelector() {
        return XpathBuilder
            .from(div, withId("header-user"))
            .child(a, atIndex(1))
            .build();
    }

    @Override
    protected By profilePageElementSelector() {
        return XpathBuilder
            .from(div, withClass("user-header"))
            .build();
    }

    @Override
    public List<By> headerSelectors() {
        return List.of(
            By.id("header")
        );
    }

    @Override
    public void unfixSidebar(final RemoteWebDriver driver) {
        final WebElement sidebar = driver.findElement(By.id("sidebar"));
        final String sidebarClasses = sidebar.getAttribute("class");

        if (sidebarClasses == null || !sidebarClasses.contains("collapsed")) {
            LOGGER.trace("Sidebar is open, toggling to close");
            final By toggleSelector = XpathBuilder
                .from(button, withAttribute("onclick", "toggleSidebar()"))
                .build();
            clickButton(driver.findElement(toggleSelector));
        }
        browserInteractionHelper().makeUnfixed(driver.findElement(By.id("sidebar-show")));
    }

    @Override
    public void translatePageToEnglish() {
        browserInteractionHelper().translatePage();
    }

    @Override
    protected By logoutButtonSelector() {
        return XpathBuilder
            .from(form, containsAttribute("action", "/logout"))
            .child(button, withType("submit"))
            .build();
    }
}
