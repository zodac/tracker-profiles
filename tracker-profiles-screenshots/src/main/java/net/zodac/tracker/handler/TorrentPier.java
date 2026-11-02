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
import static net.zodac.tracker.framework.xpath.HtmlElement.span;
import static net.zodac.tracker.framework.xpath.HtmlElement.table;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.atIndex;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.withClass;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.withId;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.withName;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.withType;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import net.zodac.tracker.framework.annotation.CommonTrackerHandler;
import net.zodac.tracker.framework.annotation.TrackerHandler;
import net.zodac.tracker.framework.driver.extension.Extension;
import net.zodac.tracker.framework.driver.extension.adblock.UblockOriginLiteExtension;
import net.zodac.tracker.framework.driver.extension.adblock.UblockOriginSetting;
import net.zodac.tracker.framework.xpath.XpathBuilder;
import net.zodac.tracker.handler.definition.HasDismissibleElement;
import net.zodac.tracker.handler.definition.UsesExtensions;
import net.zodac.tracker.util.BrowserInteractionHelper;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.remote.RemoteWebDriver;

/**
 * Common implementation of {@link AbstractTrackerHandler} for {@code TorrentPier}-based trackers.
 */
@CommonTrackerHandler("TorrentPier")
public class TorrentPier extends AbstractTrackerHandler implements HasDismissibleElement, UsesExtensions {

    @Override
    protected By usernameFieldSelector() {
        return XpathBuilder
            .from(table, withClass("forumline"))
            .descendant(input, withName("login_username"), withType("text"))
            .build();
    }

    @Override
    protected By passwordFieldSelector() {
        return XpathBuilder
            .from(table, withClass("forumline"))
            .descendant(input, withName("login_password"), withType("password"))
            .build();
    }

    @Override
    protected By loginButtonSelector() {
        return XpathBuilder
            .from(table, withClass("forumline"))
            .descendant(input, withName("login"), withType("submit"))
            .build();
    }

    @Override
    public void dismiss() {
        LOGGER.debug("\t\t- Checking for donation notice");

        final RemoteWebDriver driver = driver();
        final BrowserInteractionHelper browserInteractionHelper = browserInteractionHelper();

        // Donation banner at the bottom of the screen
        final By bottomBannerSelector = XpathBuilder
            .from(div, withId("cookieNotice"))
            .child(a, atIndex(2))
            .build();
        final Collection<WebElement> bottomBanners = driver.findElements(bottomBannerSelector);
        if (!bottomBanners.isEmpty()) {
            // There should only be one of these
            LOGGER.debug("\t\t\t- Found bottom donation banner, clearing");
            final WebElement banner = browserInteractionHelper.waitForElementToBeInteractable(bottomBannerSelector, pageLoadDuration());
            clickButton(banner);
        }

        // Donation banner in the middle of the screen
        final By mainBannerSelector = XpathBuilder
            .from(div, withClass("top-alert"), withClass("hide-for-print"))
            .child(span, atIndex(2))
            .build();
        final Collection<WebElement> mainBanners = driver.findElements(mainBannerSelector);
        if (!mainBanners.isEmpty()) {
            // There should only be one of these
            LOGGER.debug("\t\t\t- Found main donation banner, clearing");
            final WebElement banner = browserInteractionHelper.waitForElementToBeInteractable(mainBannerSelector, pageLoadDuration());
            clickButton(banner);
        }

        LOGGER.debug("\t\t- Cleared donation banner");
    }

    @Override
    protected By profileLinkSelector() {
        return By.id("logged-in-username");
    }

    @Override
    protected By profilePageElementSelector() {
        return By.id("traf-stats-tbl");
    }

    @Override
    protected By logoutButtonSelector() {
        return XpathBuilder
            .from(img, withClass("log-out-icon"))
            .build();
    }

    @Override
    protected By postLogoutElementSelector() {
        return By.id("quick-search-guest");
    }

    @Override
    public Duration maximumClickResolutionDuration() {
        return Duration.ofMinutes(2L);
    }

    @Override
    public List<Extension> requiredExtensions() {
        // Ad-blocker
        final Map<UblockOriginSetting, Boolean> settings = Map.of(
            UblockOriginSetting.ENABLE_MISCELLANEOUS_FILTERS, true,
            UblockOriginSetting.ENABLE_REGION_FILTERS, true,
            UblockOriginSetting.SET_FILTERING_MODE, true
        );

        return List.of(new UblockOriginLiteExtension(settings));
    }
}
