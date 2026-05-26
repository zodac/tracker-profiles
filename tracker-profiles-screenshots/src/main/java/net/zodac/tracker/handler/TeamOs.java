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
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.containsHref;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.withClass;

import java.util.List;
import java.util.Map;
import net.zodac.tracker.framework.annotation.TrackerHandler;
import net.zodac.tracker.framework.driver.extension.Extension;
import net.zodac.tracker.framework.driver.extension.adblock.UblockOriginLiteExtension;
import net.zodac.tracker.framework.driver.extension.adblock.UblockOriginSetting;
import net.zodac.tracker.framework.xpath.XpathBuilder;
import net.zodac.tracker.handler.definition.UsesExtensions;
import net.zodac.tracker.util.BrowserInteractionHelper;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

/**
 * Extension of the {@link Unit3dHandler} for the {@code TeamOS} tracker.
 */
@TrackerHandler(name = "TeamOS", url = "https://teamos.xyz/login/")
public class TeamOs extends XenForoHandler implements UsesExtensions {

    private static final int EXPECTED_NUMBER_OF_THREAD_LINKS = 1;

    /**
     * {@inheritDoc}
     *
     * <p>
     * For {@link TeamOs}, the tracker can request you to read a thread after logging in. We'll explicitly search for this request to read a thread.
     * If it exists, we'll open the page (forcing it into the current tab). We'll then continue with the rest of the flow.
     */
    @Override
    protected void postLoginClickAction() {
        final BrowserInteractionHelper browserInteractionHelper = browserInteractionHelper();
        browserInteractionHelper.stopPageLoad();

        // When an update to site rules occurs, there is a single button with a link to the rules thread
        final By adminThreadButtonsSelector = XpathBuilder
            .from(div, withClass("blockMessage"))
            .child(a, withClass("button"), containsHref("/threads/"))
            .build();
        final List<WebElement> adminThreadButtons = driver().findElements(adminThreadButtonsSelector).stream().toList();

        if (adminThreadButtons.size() != EXPECTED_NUMBER_OF_THREAD_LINKS) {
            LOGGER.debug("\t\t- Found {} thread links, not clicking anything", adminThreadButtons.size());
            return;
        }

        LOGGER.debug("\t\t- Found single thread link to updated rules, admin requires thread to be viewed, clicking...");
        final WebElement adminThreadButton = adminThreadButtons.getFirst();
        browserInteractionHelper.removeAttribute(adminThreadButton, "target");  // Stop forcing the link to open in a new tab
        clickButton(adminThreadButton);
        browserInteractionHelper.stopPageLoad();
        LOGGER.debug("\t\t- Updated rules thread viewed, continuing tracker execution");
    }

    @Override
    public List<Extension> requiredExtensions() {
        // Ad-blocker
        final Map<UblockOriginSetting, Boolean> settings = Map.of(
            UblockOriginSetting.ENABLE_MISCELLANEOUS_FILTERS, true,
            UblockOriginSetting.ENABLE_REGION_FILTERS, true,
            UblockOriginSetting.SET_FILTERING_MODE, false
        );

        return List.of(new UblockOriginLiteExtension(settings));
    }
}
