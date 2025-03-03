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
import net.zodac.tracker.framework.TrackerHandler;
import org.openqa.selenium.By;
import org.openqa.selenium.chrome.ChromeDriver;

/**
 * Implementation of {@link AbstractTrackerHandler} for the {@code FileList} tracker.
 */
@TrackerHandler(name = "FileList", needsManualInput = false, url = {
    "https://filelist.io/",
    "https://thefl.org/"
})
public class FileListHandler extends AbstractTrackerHandler {

    /**
     * Default constructor.
     *
     * @param driver      a {@link ChromeDriver} used to load web pages and perform UI actions
     * @param trackerUrls the URLs to the tracker
     */
    public FileListHandler(final ChromeDriver driver, final Collection<String> trackerUrls) {
        super(driver, trackerUrls);
    }

    @Override
    public By loginButtonSelector() {
        return By.xpath("//input[@type='submit' and @value='Login']");
    }

    @Override
    protected By postLoginSelector() {
        return By.id("maincolumn");
    }

    @Override
    protected By profilePageSelector() {
        return By.xpath("//div[@class='statusbar']//div/a[span]");
    }

    @Override
    protected By logoutButtonSelector() {
        return By.xpath("//a[contains(text(), 'Logout')]");
    }
}
