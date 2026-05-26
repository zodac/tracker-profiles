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
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.withId;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import net.zodac.tracker.framework.annotation.TrackerHandler;
import net.zodac.tracker.framework.xpath.XpathBuilder;
import net.zodac.tracker.handler.definition.HasProfilePageActions;
import net.zodac.tracker.util.BrowserInteractionHelper;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.remote.RemoteWebDriver;

/**
 * Extension of the {@link LuminanceHandler} for the {@code Empornium} tracker.
 */
@TrackerHandler(name = "Empornium", adult = true, url = {
    "https://emparadise.rs/",
    "https://www.empornium.sx/"
})
public class Empornium extends LuminanceHandler implements HasProfilePageActions {

    @Override
    public By loginPageSelector() {
        return XpathBuilder
            .from(div, withId("menu"))
            .child(ul, atIndex(1))
            .child(li, atIndex(2))
            .child(a, atIndex(1))
            .build();
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * For {@link Empornium}, we close additional sections on the profile page prior to screenshotting. Continues if the section does not
     * exist for the user's profile. The sections to close are:
     *
     * <ul>
     *     <li>Recent snatches</li>
     *     <li>Collages</li>
     *     <li>Uploaded torrents</li>
     * </ul>
     */
    @Override
    public void performActionOnProfilePage() {
        final RemoteWebDriver driver = driver();
        // Maps each toggle button ID to its content div ID
        final Map<String, String> buttonToDivIds = Map.of(
            "recentsnatchesbutton", "recentsnatchesdiv",
            "collagesbutton", "collagesdiv",
            "submitbutton", "torrentsdiv"
        );

        for (final Map.Entry<String, String> entry : buttonToDivIds.entrySet()) {
            // jQuery's toggle() sets display:none on the div; isDisplayed() detects this without relying on button text
            final Collection<WebElement> sectionDivs = driver.findElements(By.id(entry.getValue()));
            for (final WebElement sectionDiv : sectionDivs) {
                if (sectionDiv.isDisplayed()) {
                    LOGGER.debug("\t\t- Closing section #{}", entry.getValue());
                    clickButton(driver.findElement(By.id(entry.getKey())));
                    BrowserInteractionHelper.explicitWait(pageTransitionsDuration(), "section to close");
                }
            }
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * For {@link Empornium}, the IP address is not visible on the profile page.
     */
    @Override
    public Collection<By> ipAddressElements() {
        return List.of();
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * For {@link Empornium}, the torrent passkey is not visible on the profile page.
     */
    @Override
    public Collection<By> torrentPasskeyElements() {
        return List.of();
    }
}
