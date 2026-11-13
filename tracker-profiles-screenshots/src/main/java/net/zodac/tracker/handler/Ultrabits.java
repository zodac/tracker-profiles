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
import static net.zodac.tracker.framework.xpath.HtmlElement.body;
import static net.zodac.tracker.framework.xpath.HtmlElement.button;
import static net.zodac.tracker.framework.xpath.HtmlElement.div;
import static net.zodac.tracker.framework.xpath.HtmlElement.input;
import static net.zodac.tracker.framework.xpath.HtmlElement.main;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.atIndex;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.withAttribute;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.withClass;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.withHref;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.withType;

import net.zodac.tracker.framework.annotation.TrackerHandler;
import net.zodac.tracker.framework.config.Configuration;
import net.zodac.tracker.framework.xpath.XpathBuilder;
import net.zodac.tracker.handler.definition.HasCloudflareCheck;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.remote.RemoteWebDriver;

/**
 * Implementation of {@link AbstractTrackerHandler} for the {@code Ultrabits} tracker.
 */
@TrackerHandler(name = "Ultrabits", url = "https://ultrabits.org/")
public class Ultrabits extends AbstractTrackerHandler implements HasCloudflareCheck {

    @Override
    public By cloudflareSelector() {
        return XpathBuilder
            .from(div, withClass("cf-turnstile"))
            .child(div, atIndex(1))
            .build();
    }

    @Override
    protected By usernameFieldSelector() {
        // Special case where the website provides an option to use English, but only on the login page
        // We use this instead of an explicit translation to let the website handle it
        if (Configuration.get().enableTranslationToEnglish()) {
            LOGGER.debug("\t\t- Selecting English as tracker language prior to login");
            final RemoteWebDriver driver = driver();
            final By languageDropdownSelector = XpathBuilder
                .from(body, atIndex(1))
                .child(div, atIndex(2))
                .child(div, atIndex(1))
                .child(div, atIndex(1))
                .child(button, atIndex(1))
                .build();
            final WebElement languageDropdown = driver.findElement(languageDropdownSelector);
            clickButton(languageDropdown);

            final By englishLanguageOptionSelector = XpathBuilder
                .from(body, atIndex(1))
                .child(div, atIndex(2))
                .child(div, atIndex(1))
                .child(div, atIndex(1))
                .child(div, atIndex(1))
                .child(button, atIndex(2))
                .build();
            final WebElement englishLanguageOption = driver.findElement(englishLanguageOptionSelector);
            clickButton(englishLanguageOption);
        }

        return XpathBuilder
            .from(input, withAttribute("autocomplete", "username"))
            .build();
    }

    @Override
    protected By passwordFieldSelector() {
        return XpathBuilder
            .from(input, withAttribute("autocomplete", "current-password"))
            .build();
    }

    @Override
    protected By loginButtonSelector() {
        return XpathBuilder
            .from(button, withType("button"))
            .build();
    }

    @Override
    protected By profileLinkSelector() {
        return XpathBuilder
            .from(a, withHref("/profile"))
            .build();
    }

    @Override
    protected By profilePageElementSelector() {
        return XpathBuilder
            .from(main, atIndex(1))
            .child(div, atIndex(1))
            .child(div, atIndex(1))
            .child(div, atIndex(1))
            .child(div, atIndex(2))
            .child(div, atIndex(1))
            .child(div, atIndex(1))
            .build();
    }

    @Override
    protected By logoutButtonSelector() {
        return XpathBuilder
            .from(button, withAttribute("title", "Log out"))
            .build();
    }

    @Override
    protected By postLogoutElementSelector() {
        return XpathBuilder
            .from(input, withAttribute("autocomplete", "username"))
            .build();
    }
}
