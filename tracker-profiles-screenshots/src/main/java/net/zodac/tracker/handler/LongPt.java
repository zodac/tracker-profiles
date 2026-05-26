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
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.atIndex;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.withClass;

import net.zodac.tracker.framework.TrackerType;
import net.zodac.tracker.framework.annotation.TrackerHandler;
import net.zodac.tracker.framework.xpath.XpathBuilder;
import net.zodac.tracker.util.BrowserInteractionHelper;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

/**
 * Extension of the {@link NexusPhpHandler} for the {@code LongPT} tracker.
 */
@TrackerHandler(name = "LongPT", type = TrackerType.MANUAL, url = "https://longpt.org/")
public class LongPt extends NexusPhpHandler {

    @Override
    protected By loginButtonSelector() {
        return By.id("submit-btn");
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * For {@link LongPt}, there is a Cloudflare check AFTER login, which is non-standard. Instead of modifying
     * {@link net.zodac.tracker.handler.definition.HasCloudflareCheck} to be flexible for this one-off use-case, we just re-implement it here.
     */
    @Override
    protected void postLoginClickAction() {
        final BrowserInteractionHelper browserInteractionHelper = new BrowserInteractionHelper(driver());
        LOGGER.debug("\t- Performing Cloudflare verification check");

        browserInteractionHelper.waitForPageToLoad(pageLoadDuration());
        LOGGER.info("\t\t >>> Waiting for user to pass the Cloudflare verification");

        final By cloudflareSelector = XpathBuilder
            .from(div, withClass("main-content"))
            .descendant(div, atIndex(2))
            .build();
        final WebElement cloudflareElement = browserInteractionHelper.waitForElementToBePresent(cloudflareSelector, pageLoadDuration());
        browserInteractionHelper.highlightElement(cloudflareElement);
        displayUtils().confirm(trackerDefinition().name(), "Pass the Cloudflare verification");
    }

    @Override
    protected By profileLinkSelector() {
        return XpathBuilder
            .from(a, withClass("User_Name"))
            .build();
    }
}
