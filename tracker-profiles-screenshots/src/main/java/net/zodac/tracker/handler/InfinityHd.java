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
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.withClass;

import net.zodac.tracker.framework.annotation.TrackerHandler;
import net.zodac.tracker.framework.xpath.XpathBuilder;
import org.openqa.selenium.By;
import org.openqa.selenium.TimeoutException;

/**
 * Extension of the {@link Unit3dHandler} for the {@code InfinityHD} tracker.
 */
@TrackerHandler(name = "InfinityHD", url = "https://infinityhd.net/")
public class InfinityHd extends Unit3dHandler {

    @Override
    protected By loginButtonSelector() {
        return XpathBuilder
            .from(button, withClass("btn-launch"))
            .build();
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * {@code InfinityHD} intermittently rejects an otherwise-valid login with a captcha error. If the login cannot be confirmed, the credentials are
     * submitted once more before failing.
     */
    @Override
    public void login(final String username, final String password, final String trackerName) {
        try {
            super.login(username, password, trackerName);
        } catch (final TimeoutException e) {
            LOGGER.warn("\t- Unable to confirm login, re-attempting once");
            super.login(username, password, trackerName);
        }
    }
}
