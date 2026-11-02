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
import static net.zodac.tracker.framework.xpath.HtmlElement.span;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.withClass;

import java.util.Collection;
import java.util.List;
import net.zodac.tracker.framework.annotation.TrackerHandler;
import net.zodac.tracker.framework.xpath.XpathBuilder;
import org.openqa.selenium.By;

/**
 * Extension of the {@link Unit3dHandler} for the {@code MalayaBits} tracker.
 */
@TrackerHandler(name = "MalayaBits", url = "https://malayabits.cc/")
public class MalayaBits extends Unit3dHandler {

    @Override
    protected By profilePageElementSelector() {
        return XpathBuilder
            .from(span, withClass("profile__username"))
            .build();
    }

    @Override
    public Collection<By> emailElements() {
        return List.of(
            XpathBuilder
                .from(div, withClass("profile__info-row"))
                .child(span)
                .build()
        );
    }

    @Override
    public Collection<By> ipAddressElements() {
        return List.of(
            XpathBuilder
                .from(span, withClass("client-card__value"))
                .build()
        );
    }
}
