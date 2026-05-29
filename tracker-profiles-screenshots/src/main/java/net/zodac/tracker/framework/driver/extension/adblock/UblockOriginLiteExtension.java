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

package net.zodac.tracker.framework.driver.extension.adblock;

import static net.zodac.tracker.framework.xpath.HtmlElement.button;
import static net.zodac.tracker.framework.xpath.HtmlElement.div;
import static net.zodac.tracker.framework.xpath.HtmlElement.input;
import static net.zodac.tracker.framework.xpath.HtmlElement.nav;
import static net.zodac.tracker.framework.xpath.HtmlElement.span;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.atIndex;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.withId;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.withType;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import net.zodac.tracker.framework.driver.extension.Extension;
import net.zodac.tracker.framework.xpath.NamedHtmlElement;
import net.zodac.tracker.framework.xpath.XpathBuilder;
import net.zodac.tracker.util.BrowserInteractionHelper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.remote.RemoteWebDriver;

/**
 * Google Chrome {@link Extension} for {@code uBlock Origin Lite}, used as an ad-blocker for websites.
 */
public class UblockOriginLiteExtension implements Extension {

    private static final Logger LOGGER = LogManager.getLogger();
    private static final int Y_PIXELS_TO_SCROLL_TO_MAKE_CHECKBOXES_VISIBLE = -150;

    private final Map<UblockOriginSetting, Boolean> settings;

    /**
     * Constructs a new {@link UblockOriginLiteExtension} with the given {@link UblockOriginSetting}s.
     *
     * @param settings the {@link Map} of {@link UblockOriginSetting}s to apply when configuring the {@link Extension}
     */
    public UblockOriginLiteExtension(final Map<UblockOriginSetting, Boolean> settings) {
        this.settings = Map.copyOf(settings);
    }

    @Override
    public String id() {
        return "ddkjiahejlhfcafbddmgiahcphecmpfh";
    }

    @Override
    public String path() {
        return "/app/ublock_origin_lite.crx";
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * For {@link UblockOriginLiteExtension}, we configure the {@link Extension} by increasing the ad-blocker from the default settings. We make the
     * following changes:
     *
     * <ol>
     *     <li>Set filtering mode to 'Complete'</li>
     *     <li>Enable all 'Miscellaneous' filter lists</li>
     *     <li>Enable all 'Region' filter lists</li>
     * </ol>
     */
    @Override
    public void configure(final RemoteWebDriver driver) {
        try {
            final BrowserInteractionHelper browserInteractionHelper = BrowserInteractionHelper.of(driver);
            LOGGER.info("\t- Configuring {}", getClass().getSimpleName());
            LOGGER.debug("\t\t- Configuring with settings {}", settings);
            openExtensionConfigurationPage(driver, id());

            if (isSettingEnabled(UblockOriginSetting.SET_FILTERING_MODE)) {
                LOGGER.debug("\t\t- Setting filtering mode");
                settingFilteringMode(driver);
            }

            if (isSettingEnabled(UblockOriginSetting.ENABLE_MISCELLANEOUS_FILTERS)
                || isSettingEnabled(UblockOriginSetting.ENABLE_REGION_FILTERS)) {
                LOGGER.debug("\t\t- Opening filter lists page");
                openFilterListsPage(driver, browserInteractionHelper);

                if (isSettingEnabled(UblockOriginSetting.ENABLE_MISCELLANEOUS_FILTERS)) {
                    LOGGER.debug("\t\t- Enabling all miscellaneous filters");
                    enableAllMiscellaneousFilters(driver, browserInteractionHelper);
                }

                if (isSettingEnabled(UblockOriginSetting.ENABLE_REGION_FILTERS)) {
                    LOGGER.debug("\t\t- Expanding region filter lists");
                    expandRegionFiltersList(driver, browserInteractionHelper);

                    LOGGER.debug("\t\t- Enabling all region filters");
                    enableAllRegionFilters(driver, browserInteractionHelper);
                }
            }
        } catch (final Exception e) {
            LOGGER.debug("Error configuring {}", UblockOriginLiteExtension.class.getSimpleName(), e);
            LOGGER.warn("Error configuring {}: {}", UblockOriginLiteExtension.class.getSimpleName(), e.getMessage());
        }
    }

    private boolean isSettingEnabled(final UblockOriginSetting setting) {
        return settings.getOrDefault(setting, true);
    }

    private static void openExtensionConfigurationPage(final RemoteWebDriver driver, final String id) {
        driver.navigate().to("chrome-extension://%s/dashboard.html".formatted(id));
        BrowserInteractionHelper.explicitWait(Duration.ofSeconds(1L), "configuration page to open");
    }

    // Set filtering mode to 'Complete' option
    private static void settingFilteringMode(final RemoteWebDriver driver) {
        final By filteringModeSelector = XpathBuilder
            .from(div, withId("defaultFilteringMode"))
            .child(NamedHtmlElement.of("label"), atIndex(3))
            .child(div, atIndex(3))
            .build();
        final WebElement filteringMode = driver.findElement(filteringModeSelector);
        filteringMode.click();
    }

    private static void openFilterListsPage(final RemoteWebDriver driver, final BrowserInteractionHelper browserInteractionHelper) {
        final By filterListSelector = XpathBuilder
            .from(nav, withId("dashboard-nav"))
            .child(button, atIndex(2))
            .build();
        final WebElement filterList = driver.findElement(filterListSelector);
        filterList.click();
        browserInteractionHelper.waitForPageToLoad(Duration.ofSeconds(1L));
    }

    private static void enableAllMiscellaneousFilters(final RemoteWebDriver driver, final BrowserInteractionHelper browserInteractionHelper) {
        final By miscFilterCheckboxesSelector = XpathBuilder
            .from(div, withId("lists"))
            .child(div, atIndex(1))
            .child(div, atIndex(6))
            .child(div, atIndex(2))
            .child(div)
            .child(span, atIndex(1))
            .child(NamedHtmlElement.of("label"), atIndex(1))
            .child(span, atIndex(1))
            .descendant(input, withType("checkbox"))
            .build();

        enableAllCheckBoxes(driver, browserInteractionHelper, miscFilterCheckboxesSelector);
    }

    private static void expandRegionFiltersList(final RemoteWebDriver driver, final BrowserInteractionHelper browserInteractionHelper) {
        final By regionsListSelector = XpathBuilder
            .from(div, withId("lists"))
            .child(div, atIndex(1))
            .child(div, atIndex(7))
            .child(div, atIndex(1))
            .child(span, atIndex(2))
            .build();
        final WebElement regionsList = driver.findElement(regionsListSelector);
        browserInteractionHelper.scrollToElement(regionsList);
        regionsList.click();
        LOGGER.trace("Clicking {} to expand list", regionsList);
        BrowserInteractionHelper.explicitWait(Duration.ofSeconds(1L), "region filter lists to expand");
    }

    private static void enableAllRegionFilters(final RemoteWebDriver driver, final BrowserInteractionHelper browserInteractionHelper) {
        final By regionFilterCheckboxesSelector = XpathBuilder
            .from(div, withId("lists"))
            .child(div, atIndex(1))
            .child(div, atIndex(7))
            .child(div, atIndex(2))
            .child(div)
            .child(span, atIndex(1))
            .child(NamedHtmlElement.of("label"), atIndex(1))
            .child(span, atIndex(1))
            .descendant(input, withType("checkbox"))
            .build();

        enableAllCheckBoxes(driver, browserInteractionHelper, regionFilterCheckboxesSelector);
    }

    private static void enableAllCheckBoxes(final RemoteWebDriver driver,
                                            final BrowserInteractionHelper browserInteractionHelper,
                                            final By checkboxesSelector
    ) {
        final List<WebElement> checkboxes = driver.findElements(checkboxesSelector).stream().toList();
        final int numberOfCheckboxes = checkboxes.size();
        LOGGER.debug("\t\t\t- Found {} checkboxes", numberOfCheckboxes);

        for (int i = 0; i < numberOfCheckboxes; i++) {
            final WebElement checkbox = checkboxes.get(i);
            browserInteractionHelper.scrollToElement(checkbox);
            browserInteractionHelper.scroll(0, Y_PIXELS_TO_SCROLL_TO_MAKE_CHECKBOXES_VISIBLE);

            LOGGER.trace("Clicking checkbox {}: {}", (i + 1), checkbox.getText());
            checkbox.click();
        }
    }
}
