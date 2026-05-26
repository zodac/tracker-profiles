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
import static net.zodac.tracker.framework.xpath.HtmlElement.li;
import static net.zodac.tracker.framework.xpath.HtmlElement.ul;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.atIndex;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.containsHref;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.withClass;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.withId;

import java.util.Collection;
import java.util.List;
import net.zodac.tracker.framework.TrackerType;
import net.zodac.tracker.framework.annotation.TrackerHandler;
import net.zodac.tracker.framework.xpath.XpathBuilder;
import net.zodac.tracker.handler.redaction.RedactsEmail;
import net.zodac.tracker.handler.redaction.RedactsIpAddress;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.remote.RemoteWebDriver;

/**
 * Implementation of {@link AbstractTrackerHandler} for the {@code PassThePopcorn} tracker.
 */
@TrackerHandler(name = "PassThePopcorn", type = TrackerType.MANUAL, url = "https://passthepopcorn.me/")
public class PassThePopcorn extends AbstractTrackerHandler implements RedactsEmail, RedactsIpAddress {

    @Override
    protected By loginButtonSelector() {
        final By captchaSelector = By.id("captcha_container");
        browserInteractionHelper().waitForElementToBePresent(captchaSelector, pageLoadDuration());
        return By.id("login-button");
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * For {@link PassThePopcorn}, after clicking the login button with a successful username/password, another section pops up. There is a
     * multiple-choice question, where the correct movie title must be chosen that matches the poster, and the login button pressed again.
     *
     * <p>
     * Manual user interactions:
     * <ol>
     *     <li>Select correct answer to question</li>
     *     <li>Click the login button</li>
     * </ol>
     */
    @Override
    protected void postLoginClickAction() {
        final RemoteWebDriver driver = driver();
        final String initialUrl = driver.getCurrentUrl();
        LOGGER.info("\t\t >>> Waiting for user to select correct movie title");

        final WebElement selectionElement = driver.findElement(By.id("captcha_container"));
        browserInteractionHelper().highlightElement(selectionElement);
        displayUtils().confirm(trackerDefinition().name(), "Select the correct movie");

        // If the user didn't click 'login', do it for them
        // Special case for PTP since the login button needs to be clicked twice
        final String nextUrl = driver.getCurrentUrl();
        if (nextUrl.equalsIgnoreCase(initialUrl)) {
            final By loginButtonSelector = loginButtonSelector();
            if (loginButtonSelector != null) {
                final WebElement loginButton = driver.findElement(loginButtonSelector);
                clickButton(loginButton);
            }
        }
    }

    @Override
    protected By profileLinkSelector() {
        return XpathBuilder
            .from(li, withId("nav_userinfo"))
            .child(a, atIndex(1))
            .build();
    }

    @Override
    protected By profilePageElementSelector() {
        return XpathBuilder
            .from(a, containsHref("bprate.php"))
            .build();
    }

    @Override
    public Collection<By> emailElements() {
        return List.of(
            XpathBuilder
                .from(div, withId("content"))
                .descendant(ul, withClass("list"))
                .descendant(a)
                .build()
        );
    }

    @Override
    public Collection<By> ipAddressElements() {
        return List.of(
            // Footer with last used IP address
            XpathBuilder
                .from(div, withId("footer"))
                .descendant(a)
                .build()
        );
    }

    @Override
    protected By logoutButtonSelector() {
        return XpathBuilder
            .from(li, withId("nav_logout"))
            .child(a, atIndex(1))
            .build();
    }
}
