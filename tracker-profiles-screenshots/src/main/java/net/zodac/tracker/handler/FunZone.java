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

import static net.zodac.tracker.framework.xpath.HtmlElement.button;
import static net.zodac.tracker.framework.xpath.HtmlElement.div;
import static net.zodac.tracker.framework.xpath.HtmlElement.main;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.atIndex;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.withClass;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.withId;

import java.time.Duration;
import java.util.Collection;
import net.zodac.tracker.framework.annotation.TrackerHandler;
import net.zodac.tracker.framework.xpath.XpathBuilder;
import net.zodac.tracker.util.BrowserInteractionHelper;
import net.zodac.tracker.util.StringUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.remote.RemoteWebDriver;

/**
 * Extension of the {@link Unit3dHandler} for the {@code FunZone} tracker.
 */
@TrackerHandler(name = "FunZone", url = "https://myfunzone.org/")
public class FunZone extends Unit3dHandler {

    /**
     * {@inheritDoc}
     *
     * <p>
     * For {@link FunZone}, sometimes there is also a pop-up showing new available themes, which needs to be closed, in addition to a themes banner.
     */
    @Override
    public void dismiss() {
        // Clear this first, since it might block the screen
        clearThemesPopups();
        clearThemesBanner();
        super.dismiss(); // Standard Unit3d cookie banner
    }

    private void clearThemesPopups() {
        BrowserInteractionHelper.explicitWait(Duration.ofSeconds(2L), "theme pop-ups to load");
        LOGGER.debug("\t\t- Checking for theme pop-ups");

        final RemoteWebDriver driver = driver();
        final By popupsSelector = XpathBuilder
            .from(div, withId("themes-popup"))
            .child(div, atIndex(1))
            .child(button, atIndex(1))
            .build();

        final Collection<WebElement> popups = driver.findElements(popupsSelector);
        if (popups.isEmpty()) {
            LOGGER.debug("\t\t\t- No theme pop-ups found");
            return;
        }

        LOGGER.debug("\t\t\t- Found {} theme pop-up{}, clearing", popups.size(), StringUtils.pluralise(popups));
        for (final WebElement popup : popups) {
            final By dontShowCheckboxSelector = By.id("popup-dont-show");
            final WebElement dontShowCheckbox = driver.findElement(dontShowCheckboxSelector);
            clickButton(dontShowCheckbox);
            clickButton(popup);
        }

        LOGGER.debug("\t\t- Cleared theme pop-ups");
    }

    private void clearThemesBanner() {
        LOGGER.debug("\t\t- Checking for theme banners");

        final By bannersSelector = XpathBuilder
            .from(div, withId("themes-banner"))
            .child(div, atIndex(2))
            .child(button, atIndex(1))
            .build();

        final Collection<WebElement> banners = driver().findElements(bannersSelector);
        if (banners.isEmpty()) {
            LOGGER.debug("\t\t\t- No theme banners found");
            return;
        }

        LOGGER.debug("\t\t\t- Found {} theme banners{}, clearing", banners.size(), StringUtils.pluralise(banners));
        for (final WebElement banner : banners) {
            clickButton(banner);
        }

        LOGGER.debug("\t\t- Cleared theme banner");
    }

    @Override
    protected By profilePageElementSelector() {
        return XpathBuilder
            .from(main, withClass("page__user-profile--show"))
            .build();
    }
}
