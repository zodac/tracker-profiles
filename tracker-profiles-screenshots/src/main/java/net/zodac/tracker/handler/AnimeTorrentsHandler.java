/*
 * BSD Zero Clause License
 *
 * Copyright (c) 2024-2025 zodac.net
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

import java.util.Collection;
import java.util.List;
import net.zodac.tracker.framework.TrackerType;
import net.zodac.tracker.framework.annotation.TrackerHandler;
import org.openqa.selenium.By;
import org.openqa.selenium.remote.RemoteWebDriver;

/**
 * Implementation of {@link AbstractTrackerHandler} for the {@code AnimeTorrents} tracker.
 */
@TrackerHandler(name = "AnimeTorrents", type = TrackerType.CLOUDFLARE_CHECK, url = "https://animetorrents.me/")
public class AnimeTorrentsHandler extends AbstractTrackerHandler {

    /**
     * Default constructor.
     *
     * @param driver      a {@link RemoteWebDriver} used to load web pages and perform UI actions
     * @param trackerUrls the URLs to the tracker
     */
    public AnimeTorrentsHandler(final RemoteWebDriver driver, final Collection<String> trackerUrls) {
        super(driver, trackerUrls);
    }

    @Override
    public By loginPageSelector() {
        return By.xpath("//a[contains(text(), 'Login')]");
    }

    @Override
    protected boolean hasCloudflareCheck() {
        return true;
    }

    @Override
    protected By usernameFieldSelector() {
        return By.id("login-element-2");
    }

    @Override
    protected By passwordFieldSelector() {
        return By.id("login-element-3");
    }

    @Override
    protected By loginButtonSelector() {
        return By.id("login-element-6");
    }

    @Override
    protected By postLoginSelector() {
        return By.xpath("//div[@id='UserPanel']/ul[1]");
    }

    @Override
    protected By profilePageSelector() {
        return By.xpath("//div[@id='UserPanel']/ul[1]/li[1]/a[1]");
    }

    @Override
    public Collection<By> getElementsPotentiallyContainingSensitiveInformation() {
        return List.of(
            By.xpath("//table[@class='dataTable']/tbody[1]/tr[td[1]/strong[contains(text(), 'E-mail')]]/td[2]") // Email
        );
    }

    @Override
    protected By logoutButtonSelector() {
        return By.xpath("//ul[@id='MmOtherLinks']/li[1]/a[1]");
    }
}
