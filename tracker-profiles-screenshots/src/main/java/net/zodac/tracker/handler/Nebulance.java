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

import static net.zodac.tracker.framework.xpath.HtmlElement.div;
import static net.zodac.tracker.framework.xpath.HtmlElement.li;
import static net.zodac.tracker.framework.xpath.HtmlElement.ul;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.atIndex;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.withClass;

import java.util.Collection;
import java.util.List;
import net.zodac.tracker.framework.annotation.TrackerHandler;
import net.zodac.tracker.framework.xpath.XpathBuilder;
import net.zodac.tracker.handler.definition.HasProfilePageActions;
import org.openqa.selenium.By;

/**
 * Extension of the {@link LuminanceHandler} for the {@code Nebulance} tracker.
 */
@TrackerHandler(name = "Nebulance", url = "https://nebulance.io/")
public class Nebulance extends LuminanceHandler implements HasProfilePageActions {

    @Override
    protected By loginButtonSelector() {
        return By.name("login");
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * For {@link Nebulance}, the user's profile page cannot be scrolled by default. We override the 'body' tag to allow scrolling to occur
     * when taking the screenshot.
     */
    @Override
    public void performActionOnProfilePage() {
        driver.executeScript("document.body.style.height = 'auto';");
        driver.executeScript("document.body.style.overflowY = 'visible';");
    }

    @Override
    public Collection<By> torrentPasskeyElements() {
        return List.of(
            XpathBuilder
                .from(div, withClass("sidebar"))
                .child(div, atIndex(8))
                .child(ul, atIndex(1))
                .child(li, atIndex(7))
                .build()
        );
    }
}
