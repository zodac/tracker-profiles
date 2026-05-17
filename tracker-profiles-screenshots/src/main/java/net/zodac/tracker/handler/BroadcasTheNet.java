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
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.withClass;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.withId;

import java.util.Collection;
import java.util.List;
import net.zodac.tracker.framework.TrackerType;
import net.zodac.tracker.framework.annotation.TrackerHandler;
import net.zodac.tracker.framework.xpath.XpathBuilder;
import net.zodac.tracker.handler.definition.HasCloudflareCheck;
import net.zodac.tracker.handler.definition.HasProfilePageActions;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

/**
 * Extension of the {@link GazelleHandler} for the {@code BroadcasThe.Net} tracker.
 */
@TrackerHandler(name = "BroadcasThe.Net", type = TrackerType.MANUAL, url = "https://broadcasthe.net/")
public class BroadcasTheNet extends GazelleHandler implements HasCloudflareCheck, HasProfilePageActions {

    @Override
    public By loginPageSelector() {
        return XpathBuilder
            .from(div, withId("logo"))
            .child(a, atIndex(2))
            .build();
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * For {@link BroadcasTheNet}, the initial profile page has no user stats. We need to click the 'Info' tab to expose this information for a
     * screenshot.
     */
    @Override
    public void performActionOnProfilePage() {
        final By infoTabSelector = XpathBuilder
            .from(div, withId("slider"))
            .child(div, atIndex(1))
            .child(ul, atIndex(1))
            .child(li, atIndex(2))
            .child(a, atIndex(1))
            .build();
        final WebElement infoTabLink = driver.findElement(infoTabSelector);
        clickButton(infoTabLink);

        // Move the cursor out of the way, to avoid highlighting a tooltip for a badge
        browserInteractionHelper.moveToOrigin();
    }

    @Override
    protected By profilePageElementSelector() {
        return XpathBuilder
            .from(div, withClass("user_profile"))
            .build();
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * For {@link BroadcasTheNet}, the email address is not visible on the profile page.
     */
    @Override
    public Collection<By> emailElements() {
        return List.of();
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * For {@link BroadcasTheNet}, the IP address is not visible on the profile page.
     */
    @Override
    public Collection<By> ipAddressElements() {
        return List.of();
    }

    @Override
    protected By logoutButtonSelector() {
        return XpathBuilder
            .from(ul, withId("userinfo_username"))
            .child(li, atIndex(3))
            .child(a, atIndex(1))
            .build();
    }
}
