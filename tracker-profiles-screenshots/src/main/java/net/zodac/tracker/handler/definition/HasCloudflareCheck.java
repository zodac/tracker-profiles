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

package net.zodac.tracker.handler.definition;

import static net.zodac.tracker.framework.xpath.HtmlElement.div;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.atIndex;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.withClass;

import java.time.Duration;
import net.zodac.tracker.framework.gui.DisplayUtils;
import net.zodac.tracker.framework.xpath.XpathBuilder;
import net.zodac.tracker.util.BrowserInteractionHelper;
import org.apache.logging.log4j.LogManager;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.remote.RemoteWebDriver;

/**
 * Marks an {@link net.zodac.tracker.handler.AbstractTrackerHandler} as having a Cloudflare verification check on the login page that must be passed
 * before logging in.
 */
public interface HasCloudflareCheck {

    /**
     * Performs the Cloudflare verification check on the login page. The user must manually pass the check before execution continues.
     *
     * <p>
     * Manual user interactions:
     * <ol>
     *     <li>Pass the Cloudflare verification check</li>
     * </ol>
     *
     * @param driver           the {@link RemoteWebDriver} for the current session
     * @param pageLoadDuration the duration to wait for page elements to load
     * @param trackerName      the name of the tracker
     */
    default void cloudflareCheck(final RemoteWebDriver driver, final Duration pageLoadDuration, final String trackerName) {
        final BrowserInteractionHelper browserInteractionHelper = BrowserInteractionHelper.of(driver);
        final var logger = LogManager.getLogger(HasCloudflareCheck.class);
        logger.debug("\t- Performing Cloudflare verification check");

        browserInteractionHelper.waitForPageToLoad(pageLoadDuration);
        logger.info("\t\t >>> Waiting for user to pass the Cloudflare verification");

        final By cloudflareSelector = cloudflareSelector();
        final WebElement cloudflareElement = browserInteractionHelper.waitForElementToBePresent(cloudflareSelector, pageLoadDuration);
        browserInteractionHelper.highlightElement(cloudflareElement);
        DisplayUtils.withDriver(driver).confirm(trackerName, "Pass the Cloudflare verification");
    }

    /**
     * Returns the {@link By} selector for the Cloudflare verification element.
     *
     * @return the {@link By} selector for the Cloudflare element
     */
    default By cloudflareSelector() {
        return XpathBuilder
            .from(div, withClass("main-content"))
            .descendant(div, atIndex(2))
            .build();
    }
}
