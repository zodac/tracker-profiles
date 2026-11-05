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
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.atIndex;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.withClass;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.withType;

import java.util.Collection;
import net.zodac.tracker.framework.annotation.TrackerHandler;
import net.zodac.tracker.framework.xpath.XpathBuilder;
import net.zodac.tracker.handler.definition.HasDismissibleElement;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.remote.RemoteWebDriver;

/**
 * Extension of the {@link Unit3dHandler} for the {@code CapybaraBR} tracker.
 */
@TrackerHandler(name = "CapybaraBR", url = "https://capybarabr.com/")
public class CapybaraBr extends Unit3dHandler implements HasDismissibleElement {

    @Override
    protected By loginButtonSelector() {
        return XpathBuilder
            .from(button, withClass("capy-button-primary"), withType("submit"))
            .build();
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * For {@link CapybaraBr}, there is a warning to enable 2FA if it's not configured on your account.
     */
    @Override
    public void dismiss() {
        LOGGER.debug("\t\t- Checking for 2FA warning");
        final RemoteWebDriver driver = driver();
        final By twoFaReminderSelectors = XpathBuilder
            .from(div, withClass("two-factor-reminder__body"))
            .build();
        final Collection<WebElement> twoFaReminders = driver.findElements(twoFaReminderSelectors);
        if (twoFaReminders.isEmpty()) {
            return;
        }

        LOGGER.debug("\t\t\t- Found 2FA reminder, closing");
        final By closeButtonSelector = XpathBuilder
            .from(div, withClass("two-factor-reminder__body"))
            .child(div, withClass("two-factor-reminder__actions"))
            .child(button, atIndex(1))
            .build();
        final WebElement closeButton = driver.findElement(closeButtonSelector);
        clickButton(closeButton);

        LOGGER.debug("\t\t\t- Closed reminder");
    }
}
