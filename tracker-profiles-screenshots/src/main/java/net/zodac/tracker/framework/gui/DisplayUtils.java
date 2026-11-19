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

import java.util.Objects;
import net.zodac.tracker.framework.exception.CancelledInputException;
import net.zodac.tracker.framework.exception.NoUserInputException;
import net.zodac.tracker.framework.input.UserInputRequests;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jspecify.annotations.Nullable;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.remote.RemoteWebDriver;

/**
 * Utility class used to ask the user for an input (such as solving a captcha) in the browser of a manual tracker. The request is shown in the web
 * UI and in an {@link InputDialog} in front of the browser, either of which the user answers once they have provided the input in the browser.
 */
public final class DisplayUtils {

    private static final Logger LOGGER = LogManager.getLogger();

    private final RemoteWebDriver driver;

    private DisplayUtils(final RemoteWebDriver driver) {
        this.driver = driver;
    }

    /**
     * Creates a {@link DisplayUtils} bound to the given {@link RemoteWebDriver}.
     *
     * @param driver the {@link RemoteWebDriver} used to poll for page changes
     * @return the {@link DisplayUtils}
     */
    public static DisplayUtils withDriver(final RemoteWebDriver driver) {
        return new DisplayUtils(driver);
    }

    /**
     * Asks the user (in the web UI, and in an {@link InputDialog} in front of the browser) to provide an input to the loaded tracker, and waits
     * until they confirm it has been provided, or the page in the browser changes.
     *
     * @param trackerName the name of the tracker waiting for the input
     * @param label       what the user needs to do
     * @throws CancelledInputException thrown if the user chooses to skip the tracker
     * @throws NoUserInputException    thrown if no input is received in time
     * @see UserInputRequests#await(String, String, java.util.function.BooleanSupplier, net.zodac.tracker.framework.input.UserInputPrompt)
     */
    public void confirm(final String trackerName, final String label) {
        final String initialUrl = currentUrl();
        final String initialTitle = currentTitle();
        LOGGER.trace("Starting page change listener (initial url: '{}', initial title: '{}')", initialUrl, initialTitle);

        UserInputRequests.await(trackerName, label, () -> hasPageChanged(initialUrl, initialTitle), InputDialog.of(trackerName, label));
    }

    private boolean hasPageChanged(final @Nullable String initialUrl, final @Nullable String initialTitle) {
        try {
            final String currentUrl = driver.getCurrentUrl();
            final String currentTitle = driver.getTitle();
            if (!Objects.equals(currentUrl, initialUrl) || !Objects.equals(currentTitle, initialTitle)) {
                LOGGER.trace("Page change detected (url: '{}' -> '{}', title: '{}' -> '{}')", initialUrl, currentUrl, initialTitle, currentTitle);
                return true;
            }
        } catch (final WebDriverException e) {
            LOGGER.trace("Error polling for page change", e);
        }
        return false;
    }

    private @Nullable String currentUrl() {
        try {
            return driver.getCurrentUrl();
        } catch (final WebDriverException e) {
            LOGGER.trace("Unable to read the current URL", e);
            return null;
        }
    }

    private @Nullable String currentTitle() {
        try {
            return driver.getTitle();
        } catch (final WebDriverException e) {
            LOGGER.trace("Unable to read the current title", e);
            return null;
        }
    }
}
