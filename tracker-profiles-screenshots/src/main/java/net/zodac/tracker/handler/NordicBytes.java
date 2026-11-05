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
import static net.zodac.tracker.framework.xpath.HtmlElement.footer;
import static net.zodac.tracker.framework.xpath.HtmlElement.form;
import static net.zodac.tracker.framework.xpath.HtmlElement.li;
import static net.zodac.tracker.framework.xpath.HtmlElement.span;
import static net.zodac.tracker.framework.xpath.HtmlElement.ul;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.atIndex;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.withClass;

import java.util.Collection;
import java.util.List;
import net.zodac.tracker.framework.annotation.TrackerHandler;
import net.zodac.tracker.framework.xpath.XpathBuilder;
import net.zodac.tracker.handler.definition.HasDismissibleElement;
import net.zodac.tracker.handler.redaction.RedactsIpAddress;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.remote.RemoteWebDriver;

/**
 * Implementation of {@link AbstractTrackerHandler} for the {@code NordicBytes} tracker.
 */
@TrackerHandler(name = "NordicBytes", url = "https://nordicbytes.org/")
public class NordicBytes extends AbstractTrackerHandler implements HasDismissibleElement, RedactsIpAddress {

    @Override
    protected By loginButtonSelector() {
        return XpathBuilder
            .from(form, atIndex(1))
            .child(button, atIndex(1))
            .build();
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * For {@link NordicBytes}, there is sometimes a pop-up with the latest site updates, which blocks links to the profile page. We'll check for
     * this, then close it if found.
     */
    @Override
    public void postLoginClickAction() {
        LOGGER.debug("\t\t- Checking for site changes modal");

        final By siteChangesSelector = XpathBuilder
            .from(footer, withClass("__dialog-footer"))
            .descendant(button, withClass("btn-primary"))
            .build();
        final Collection<WebElement> siteChangesElements = driver().findElements(siteChangesSelector);

        if (siteChangesElements.isEmpty()) {
            LOGGER.debug("\t\t\t- No site changes found");
            return;
        }

        LOGGER.debug("\t\t\t- Found {} site changes, clearing", siteChangesElements.size());
        for (final WebElement siteChangesElement : siteChangesElements) {
            clickButton(siteChangesElement);
        }

        LOGGER.debug("\t\t- Cleared site changes");
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * For {@link NordicBytes}, there is a pop-up for global freeleech at times.
     */
    @Override
    public void dismiss() {
        LOGGER.debug("\t\t- Checking for Freeleech pop-up");
        final RemoteWebDriver driver = driver();
        final By twoFaReminderSelectors = XpathBuilder
            .from(div, withClass("fl-celebration__stage"))
            .build();
        final Collection<WebElement> twoFaReminders = driver.findElements(twoFaReminderSelectors);
        if (twoFaReminders.isEmpty()) {
            return;
        }

        LOGGER.debug("\t\t\t- Found Freeleech pop-up, closing");
        final By closeButtonSelector = XpathBuilder
            .from(button, withClass("celebration__close"))
            .build();
        final WebElement closeButton = driver.findElement(closeButtonSelector);
        clickButton(closeButton);

        LOGGER.debug("\t\t\t- Closed pop-up");
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * For {@link NordicBytes}, the {@code <body>} is constrained to the viewport height, so {@code window.scrollTo()} has no effect and only the
     * first viewport is captured. By default, this is only corrected by {@link #actionAfterScreenshot()}, so it must also be applied before the first
     * screenshot.
     */
    @Override
    public void actionBeforeScreenshot() {
        browserInteractionHelper().showScrollbar();
        super.actionBeforeScreenshot();
    }

    @Override
    protected By profileLinkSelector() {
        return XpathBuilder
            .from(div, withClass("__navbar-username"))
            .child(a, atIndex(1))
            .build();
    }

    @Override
    protected By profilePageElementSelector() {
        return XpathBuilder
            .from(div, withClass("__profile-header"))
            .build();
    }

    @Override
    public Collection<By> ipAddressElements() {
        return List.of(
            XpathBuilder
                .from(span, withClass("__profile-client-ip"))
                .build()
        );
    }

    @Override
    protected By logoutButtonSelector() {
        return XpathBuilder
            .from(div, withClass("__navbar-wrapper"))
            .child(ul, atIndex(3))
            .child(li, atIndex(2))
            .child(form, atIndex(1))
            .child(button, atIndex(1))
            .build();
    }
}
