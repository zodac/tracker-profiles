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

package me.zodac.tracker;

import static me.zodac.tracker.util.Executor.executeWithDriver;

import java.io.File;
import java.util.List;
import me.zodac.tracker.framework.TrackerHandler;
import me.zodac.tracker.framework.TrackerHandlerFactory;
import me.zodac.tracker.util.ScreenshotTaker;
import me.zodac.tracker.util.ScriptExecutor;
import me.zodac.tracker.framework.TrackerCsvReader;
import me.zodac.tracker.framework.TrackerInfo;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.WebElement;

public final class ProfileScreenshotter {

    private static final Logger LOGGER = LogManager.getLogger();

    // Config
    private static final String OUTPUT_DIRECTORY_PATH = "./screenshots";
    private static final boolean PREVIEW_SCREENSHOT = true;

    private ProfileScreenshotter() {

    }

    public static void main(final String[] args) {
        final List<TrackerInfo> trackerInfos = TrackerCsvReader.readTrackerInfo();

        for (final TrackerInfo trackerInfo : trackerInfos) {
            LOGGER.info("{}", trackerInfo.name());

            LOGGER.info("\t- Opening login page at '{}'", trackerInfo.loginLink());
            final TrackerHandler trackerHandler = TrackerHandlerFactory.getHandler(trackerInfo.name());

            executeWithDriver(driver -> {
                trackerHandler.openLoginPage(driver, trackerInfo);
                LOGGER.info("\t- Logging in as '{}'", trackerInfo.username());
                trackerHandler.login(driver, trackerInfo);

                LOGGER.info("\t- Logged in, redirecting to '{}'", trackerInfo.profilePage());
                trackerHandler.openProfilePage(driver, trackerInfo);

                if (trackerHandler.canCookieBannerBeCleared(driver)) {
                    LOGGER.info("\t- Cookie banner has been cleared");
                }

                final List<WebElement> elementsToBeMasked = trackerHandler.getElementsToBeMasked(driver);
                for (final WebElement elementToBeMasked : elementsToBeMasked) {
                    ScriptExecutor.maskInnerTextOfElement(driver, elementToBeMasked);
                }
                if (!elementsToBeMasked.isEmpty()) {
                    final String plural = elementsToBeMasked.size() == 1 ? "" : "s";
                    LOGGER.info("\t- Masked the text of '{}' element{}", elementsToBeMasked.size(), plural);
                }

                final File screenshot = ScreenshotTaker.takeScreenshot(driver, trackerInfo.name(), OUTPUT_DIRECTORY_PATH, PREVIEW_SCREENSHOT);
                LOGGER.info("\t- Screenshot saved at: {}", screenshot.getAbsolutePath());
                LOGGER.info("");
            });
        }
    }
}
