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

import java.time.Duration;
import net.zodac.tracker.framework.annotation.TrackerHandler;
import net.zodac.tracker.util.BrowserInteractionHelper;
import org.openqa.selenium.WebElement;

/**
 * Extension of the {@link Unit3dHandler} for the {@code Racing4Everyone} tracker.
 */
@TrackerHandler(name = "Racing4Everyone", url = "https://racing4everyone.eu/")
public class Racing4Everyone extends Unit3dHandler {

    /**
     * {@inheritDoc}
     *
     * <p>
     * For {@link Racing4Everyone}, the initial click of the login button doesn't always work, so we do it again here.
     *
     * <p>
     * In addition, we often get an error page stating the page has expired. A normal reload doesn't work, so we perform a hard reload, which will
     * complete the login.
     *
     * @see net.zodac.tracker.util.BrowserInteractionHelper#hardReloadPage(Duration)
     */
    @Override
    protected void postLoginClickAction() {
        final BrowserInteractionHelper browserInteractionHelper = browserInteractionHelper();
        final WebElement loginButton = browserInteractionHelper.waitForElementToBeInteractable(loginButtonSelector(), pageLoadDuration());
        LOGGER.trace("Clicking login button, again: {}", loginButton);
        clickButton(loginButton);

        browserInteractionHelper.hardReloadPage(pageLoadDuration());
    }
}
