/*
 * BSD Zero Clause License
 *
 * Copyright (c) 2021-2024 zodac.me
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

package me.zodac.tracker.framework;

import java.util.List;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

public abstract class TrackerHandler {

    protected static final String HOST_IP_ADDRESS = System.getProperty("HOST_IP_ADDRESS", System.getenv("HOST_IP_ADDRESS"));
    protected static final String EMAIL_ADDRESS = System.getProperty("EMAIL_ADDRESS", System.getenv("EMAIL_ADDRESS"));

    public abstract void openLoginPage(final WebDriver driver, final TrackerInfo trackerInfo);

    public abstract void login(final WebDriver driver, final TrackerInfo trackerInfo);

    public abstract void openProfilePage(final WebDriver driver, final TrackerInfo trackerInfo);

    public abstract boolean canCookieBannerBeCleared(final WebDriver driver);

    public abstract List<WebElement> getElementsToBeMasked(final WebDriver driver);
}
