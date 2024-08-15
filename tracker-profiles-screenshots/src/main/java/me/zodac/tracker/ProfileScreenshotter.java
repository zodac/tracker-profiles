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

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;
import java.util.NoSuchElementException;
import me.zodac.tracker.framework.Configuration;
import me.zodac.tracker.framework.ConfigurationProperties;
import me.zodac.tracker.framework.TrackerCsvReader;
import me.zodac.tracker.framework.TrackerDefinition;
import me.zodac.tracker.framework.TrackerHandlerFactory;
import me.zodac.tracker.handler.AbstractTrackerHandler;
import me.zodac.tracker.util.ScreenshotTaker;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

/**
 * Main driver class, which takes a screenshot of the profile page of each tracker listed in the {@code trackers.csv} input file.
 */
public final class ProfileScreenshotter {

    private static final Logger LOGGER = LogManager.getLogger();
    private static final ConfigurationProperties CONFIG = Configuration.get();

    private ProfileScreenshotter() {

    }

    /**
     * Parses the {@code trackers.csv} input file using {@link TrackerCsvReader}, then iterates through each {@link TrackerDefinition}. For each
     * tracker a {@link AbstractTrackerHandler} is retrieved and used to navigate to the tracker's profile page (after logging in and any other
     * required actions). At this point, any sensitive information is redacted, and then a screenshot is taken by {@link ScreenshotTaker}, then saved
     * in the {@link ConfigurationProperties#outputDirectory()}.
     *
     * @param args input arguments, unused
     * @throws IOException        thrown on error parsing CSV input file
     * @throws URISyntaxException thrown on error reading CSV input file
     */
    public static void main(final String[] args) throws IOException, URISyntaxException {
        final List<TrackerDefinition> trackerDefinitions = TrackerCsvReader.readTrackerInfo();
        LOGGER.info("Taking screenshots for {} trackers, saving to: '{}'", trackerDefinitions.size(), CONFIG.outputDirectory());

        for (final TrackerDefinition trackerDefinition : trackerDefinitions) {
            LOGGER.info("");
            LOGGER.info("{}", trackerDefinition.name());
            final ChromeDriver driver = createDriver();

            try {
                final AbstractTrackerHandler trackerHandler = TrackerHandlerFactory.getHandler(trackerDefinition.name(), driver);
                takeScreenshotOfProfilePage(driver, trackerHandler, trackerDefinition);
            } catch (final NoSuchElementException e) {
                LOGGER.warn("\t- No implementation for tracker '{}'", trackerDefinition.name());
            } finally {
                driver.quit();
            }
        }
    }

    private static void takeScreenshotOfProfilePage(final ChromeDriver driver,
                                                    final AbstractTrackerHandler trackerHandler,
                                                    final TrackerDefinition trackerDefinition
    ) throws IOException {
        LOGGER.info("\t- Opening login page at '{}'", trackerDefinition.loginLink());
        trackerHandler.openLoginPage(trackerDefinition);
        LOGGER.info("\t- Logging in as '{}'", trackerDefinition.username());
        trackerHandler.login(trackerDefinition);

        if (trackerHandler.canCookieBannerBeCleared()) {
            LOGGER.info("\t- Cookie banner has been cleared");
        }

        LOGGER.info("\t- Redirecting to user profile page at '{}'", trackerDefinition.profilePage());
        trackerHandler.openProfilePage(trackerDefinition);

        final int numberOfRedactedElements = trackerHandler.redactSensitiveElements();
        if (numberOfRedactedElements != 0) {
            final String plural = numberOfRedactedElements == 1 ? "" : "s";
            LOGGER.info("\t- Redacted the text of '{}' element{}", numberOfRedactedElements, plural);
        }

        final File screenshot = ScreenshotTaker.takeScreenshot(driver, trackerDefinition.name(), trackerHandler.zoomLevelForScreenshot());
        LOGGER.info("\t- Screenshot saved at: [{}]", screenshot.getAbsolutePath());

        trackerHandler.logout();
        LOGGER.info("\t- Logged out");
        LOGGER.info("");
    }

    private static ChromeDriver createDriver() {
        final ChromeOptions chromeOptions = new ChromeOptions();

        if (CONFIG.useHeadlessBrowser()) {
            chromeOptions.addArguments("--headless=new");
        }
        return new ChromeDriver(chromeOptions);
    }
}
