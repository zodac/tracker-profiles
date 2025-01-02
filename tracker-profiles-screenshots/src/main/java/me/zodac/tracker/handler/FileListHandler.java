/*
 * BSD Zero Clause License
 *
 * Copyright (c) 2021-2025 zodac.me
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

package me.zodac.tracker.handler;

import me.zodac.tracker.framework.TrackerAccessibility;
import me.zodac.tracker.framework.TrackerHandlerType;
import org.openqa.selenium.By;
import org.openqa.selenium.chrome.ChromeDriver;

/**
 * Implementation of {@link AbstractTrackerHandler} for the {@code FileList} tracker.
 */
@TrackerHandlerType(trackerName = "FileList", accessibility = TrackerAccessibility.PRIVATE)
public class FileListHandler extends AbstractTrackerHandler {

    /**
     * Default constructor.
     *
     * @param driver a {@link ChromeDriver} used to load web pages and perform UI actions
     */
    public FileListHandler(final ChromeDriver driver) {
        super(driver);
    }

    @Override
    public By loginButtonSelector() {
        return By.xpath("//input[@type='submit' and @value='Login']");
    }

    @Override
    protected By logoutButtonSelector() {
        return By.xpath("//a[contains(text(), 'Logout')]");
    }
}
