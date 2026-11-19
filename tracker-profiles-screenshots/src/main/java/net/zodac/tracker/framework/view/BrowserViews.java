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

package net.zodac.tracker.framework.view;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import net.zodac.tracker.framework.driver.BrowserDevTools;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.remote.RemoteWebDriver;

/**
 * The {@link BrowserView} of each tracker whose browser is open, by tracker name (case-insensitive). A tracker's view is registered when its handler
 * acquires a browser, and closed when the handler releases it, so the web UI can only see a browser while a tracker is using it.
 */
public final class BrowserViews {

    private static final Logger LOGGER = LogManager.getLogger();
    private static final Lock LOCK = new ReentrantLock();
    private static final Map<String, BrowserView> VIEWS = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

    private BrowserViews() {

    }

    /**
     * Registers the browser of a tracker, replacing (and closing) any view the tracker already had.
     *
     * @param trackerName the name of the tracker
     * @param driver      the {@link RemoteWebDriver} of the tracker's browser
     */
    public static void register(final String trackerName, final RemoteWebDriver driver) {
        final String debuggerAddress = BrowserDevTools.debuggerAddress(driver);
        if (debuggerAddress == null) {
            LOGGER.debug("Browser did not report its DevTools address, so it cannot be shown in the web UI");
            return;
        }

        final BrowserView view;
        try {
            view = BrowserView.forTab(debuggerAddress, driver.getWindowHandle());
        } catch (final WebDriverException | IllegalArgumentException e) {
            LOGGER.debug("Unable to find the tab of the browser, so it cannot be shown in the web UI", e);
            return;
        }

        final BrowserView previous;
        LOCK.lock();
        try {
            previous = VIEWS.put(trackerName, view);
        } finally {
            LOCK.unlock();
        }
        if (previous != null) {
            previous.close();
        }
    }

    /**
     * Closes and removes the view of a tracker, if it has one.
     *
     * @param trackerName the name of the tracker
     */
    public static void unregister(final String trackerName) {
        final BrowserView view;
        LOCK.lock();
        try {
            view = VIEWS.remove(trackerName);
        } finally {
            LOCK.unlock();
        }
        if (view != null) {
            view.close();
        }
    }

    /**
     * Retrieves the view of a tracker's browser.
     *
     * @param trackerName the name of the tracker
     * @return the {@link BrowserView}, or {@link Optional#empty()} if the tracker has no browser open
     */
    public static Optional<BrowserView> find(final String trackerName) {
        LOCK.lock();
        try {
            return Optional.ofNullable(VIEWS.get(trackerName));
        } finally {
            LOCK.unlock();
        }
    }

    /**
     * Closes and removes every view, once an execution has ended.
     */
    public static void closeAll() {
        final List<BrowserView> views;
        LOCK.lock();
        try {
            views = new ArrayList<>(VIEWS.values());
            VIEWS.clear();
        } finally {
            LOCK.unlock();
        }
        views.forEach(BrowserView::close);
    }
}
