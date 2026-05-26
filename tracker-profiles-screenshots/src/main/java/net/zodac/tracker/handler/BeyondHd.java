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
import static net.zodac.tracker.framework.xpath.HtmlElement.form;
import static net.zodac.tracker.framework.xpath.HtmlElement.img;
import static net.zodac.tracker.framework.xpath.HtmlElement.input;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.atIndex;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.withClass;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.withId;
import static net.zodac.tracker.framework.xpath.XpathAxis.parent;

import java.time.Duration;
import java.util.List;
import net.zodac.tracker.framework.TrackerType;
import net.zodac.tracker.framework.annotation.TrackerHandler;
import net.zodac.tracker.framework.xpath.XpathBuilder;
import net.zodac.tracker.handler.definition.HasFixedHeader;
import net.zodac.tracker.handler.definition.HasJumpButtons;
import net.zodac.tracker.util.BrowserInteractionHelper;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

/**
 * Implementation of {@link AbstractTrackerHandler} for the {@code BeyondHD} tracker.
 */
@TrackerHandler(name = "BeyondHD", type = TrackerType.MANUAL, url = "https://beyond-hd.me/")
public class BeyondHd extends AbstractTrackerHandler implements HasFixedHeader, HasJumpButtons {

    /**
     * {@inheritDoc}
     *
     * <p>
     * For {@link BeyondHd}, prior to clicking the login button with a successful username/password there is another field where a Captcha
     * needs to be entered.
     *
     * <p>
     * Manual user interaction:
     * <ol>
     *     <li>Enter correct captcha value</li>
     * </ol>
     */
    @Override
    protected void preLoginClickAction() {
        LOGGER.info("\t\t >>> Waiting for user to enter captcha");

        final By captchaSelector = XpathBuilder
            .from(input, withId("captcha"))
            .navigateTo(parent(div))
            .build();
        final WebElement captchaElement = driver().findElement(captchaSelector);
        browserInteractionHelper().highlightElement(captchaElement);
        displayUtils().confirm(trackerDefinition().name(), "Solve the captcha");
    }

    @Override
    protected By profileLinkSelector() {
        return XpathBuilder
            .from(img, withClass("beta-image-avatar"))
            .navigateTo(parent(a))
            .build();
    }

    @Override
    protected By profilePageElementSelector() {
        return XpathBuilder
            .from(img, withClass("img-circle"))
            .build();
    }

    @Override
    public List<By> headerSelectors() {
        return List.of(
            By.id("stickyBar")
        );
    }

    @Override
    public List<By> jumpButtonSelectors() {
        return List.of(
            By.id("back-to-top")
        );
    }

    @Override
    protected By logoutButtonSelector() {
        // Highlight the nav bar to make the logout button interactable
        final BrowserInteractionHelper browserInteractionHelper = browserInteractionHelper();
        final By logoutParentSelector = XpathBuilder
            .from(div, withClass("dropmenu"))
            .build();
        final WebElement logoutParent = driver().findElement(logoutParentSelector);
        browserInteractionHelper.moveTo(logoutParent);

        final By logoutSelector = XpathBuilder
            .from(form, withId("logout-form1"))
            .navigateTo(parent(div))
            .child(a, atIndex(1))
            .build();

        browserInteractionHelper.waitForElementToBeInteractable(logoutSelector, Duration.ofSeconds(1L));
        return logoutSelector;
    }
}
