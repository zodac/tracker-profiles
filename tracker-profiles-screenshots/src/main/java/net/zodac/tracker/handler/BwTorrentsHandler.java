/*
 * BSD Zero Clause License
 *
 * Copyright (c) 2021-2025 zodac.net
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
import net.zodac.tracker.framework.TrackerHandlerType;
import org.openqa.selenium.By;
import org.openqa.selenium.chrome.ChromeDriver;

/**
 * Implementation of {@link AbstractTrackerHandler} for the {@code BwTorrents} tracker.
 */
@TrackerHandlerType(trackerName = "BwTorrents")
public class BwTorrentsHandler extends AbstractTrackerHandler {

    /**
     * Default constructor.
     *
     * @param driver a {@link ChromeDriver} used to load web pages and perform UI actions
     */
    public BwTorrentsHandler(final ChromeDriver driver) {
        super(driver);
    }

    @Override
    protected By usernameFieldSelector() {
        return By.xpath("//input[@name='username' and @type='text' and @placeholder='Username']");
    }

    @Override
    protected By passwordFieldSelector() {
        return By.xpath("//input[@name='password' and @type='password' and @placeholder='Password']");
    }

    @Override
    public By loginButtonSelector() {
        return By.xpath("//input[@value='LOG IN' and @type='submit']");
    }

    @Override
    protected Collection<By> getElementsPotentiallyContainingSensitiveInformation() {
        return List.of(
            By.tagName("a")
        );
    }

    @Override
    protected By logoutButtonSelector() {
        return By.xpath("//div[@id='right-sts-aeon']//a[@title='Logout']");
    }
}
