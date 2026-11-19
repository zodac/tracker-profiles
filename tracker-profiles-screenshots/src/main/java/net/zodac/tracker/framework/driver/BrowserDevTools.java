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

package net.zodac.tracker.framework.driver;

import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.openqa.selenium.remote.RemoteWebDriver;

/**
 * Utility class to find the Chrome DevTools Protocol endpoint of a browser created by {@link JavaWebDriverFactory}, used by tools that connect to
 * the browser directly rather than through {@code chromedriver} (the Cloudflare solver and the live browser view of the web UI).
 */
public final class BrowserDevTools {

    private static final String CHROME_OPTIONS_CAPABILITY = "goog:chromeOptions";
    private static final String DEBUGGER_ADDRESS_KEY = "debuggerAddress";

    private BrowserDevTools() {

    }

    /**
     * Retrieves the address of the browser's DevTools endpoint (such as {@code localhost:41233}), which {@code chromedriver} reports in the session
     * capabilities.
     *
     * @param driver the {@link RemoteWebDriver}
     * @return the address, or {@code null} if the browser did not report one
     */
    public static @Nullable String debuggerAddress(final RemoteWebDriver driver) {
        final Object chromeOptions = driver.getCapabilities().getCapability(CHROME_OPTIONS_CAPABILITY);
        if (chromeOptions instanceof final Map<?, ?> options && options.get(DEBUGGER_ADDRESS_KEY) instanceof final String address) {
            return address;
        }
        return null;
    }
}
