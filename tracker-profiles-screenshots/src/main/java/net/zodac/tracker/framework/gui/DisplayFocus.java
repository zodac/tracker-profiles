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

package net.zodac.tracker.framework.gui;

import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.chromium.HasCdp;
import org.openqa.selenium.remote.RemoteWebDriver;

/**
 * Guards the input focus of the virtual display shared by every browser with a UI, as manual trackers run in parallel.
 *
 * <p>
 * Almost every interaction with a browser (Selenium commands, and the user's input sent with the Chrome DevTools Protocol) is sent to its tab
 * directly, whichever window has the focus. Keys sent to the display itself (with {@link java.awt.Robot}) instead reach the focused window, so an
 * action using them must hold {@link #lock()} and bring its browser to the front first ({@link #bringToFront(RemoteWebDriver)}). Creating a browser
 * with a UI also holds it, as a new window takes the focus.
 */
public final class DisplayFocus {

    private static final Logger LOGGER = LogManager.getLogger();
    private static final Lock LOCK = new ReentrantLock();

    private DisplayFocus() {

    }

    /**
     * The {@link Lock} to hold while an action depends on which window has the focus of the virtual display.
     *
     * @return the {@link Lock}
     */
    public static Lock lock() {
        return LOCK;
    }

    /**
     * Brings the browser's tab to the front, giving its window the focus of the virtual display. Should only be called while holding {@link #lock()}.
     *
     * @param driver the {@link RemoteWebDriver} of the browser
     */
    public static void bringToFront(final RemoteWebDriver driver) {
        if (driver instanceof final HasCdp cdp) {
            LOGGER.trace("Bringing browser to the front");
            cdp.executeCdpCommand("Page.bringToFront", Map.of());
        } else {
            LOGGER.trace("Driver does not support the Chrome DevTools Protocol, unable to bring browser to the front");
        }
    }
}
