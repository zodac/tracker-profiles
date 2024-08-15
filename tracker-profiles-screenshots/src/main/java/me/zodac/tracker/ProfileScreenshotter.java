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
import java.util.Collection;
import java.util.List;
import me.zodac.tracker.framework.TrackerCsvReader;
import me.zodac.tracker.framework.TrackerDefinition;
import me.zodac.tracker.framework.TrackerHandler;
import me.zodac.tracker.framework.TrackerHandlerFactory;
import me.zodac.tracker.util.ScreenshotTaker;
import me.zodac.tracker.util.ScriptExecutor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

/**
 * Main driver class, which takes a screenshot of the profile page of each tracker listed in the {@code trackers.csv} input file.
 */
public final class ProfileScreenshotter {

    private static final Logger LOGGER = LogManager.getLogger();

    // Config
    private static final String OUTPUT_DIRECTORY_PATH = "./screenshots";
    private static final boolean PREVIEW_SCREENSHOT = true;

    private ProfileScreenshotter() {

    }

    /**
     * Parses the {@code trackers.csv} input file using {@link TrackerCsvReader}, then iterates through each {@link TrackerDefinition}. For each
     * tracker a {@link TrackerHandler} is retrieved and used to navigate to the tracker's profile page (after logging in and any other required
     * actions). At this point, any sensitive information is masked, and then a screenshot is taken by {@link ScreenshotTaker}, then saved in the
     * {@value #OUTPUT_DIRECTORY_PATH}.
     *
     * @param args input arguments, unused
     * @throws IOException        thrown on error parsing CSV input file
     * @throws URISyntaxException thrown on error reading CSV input file
     */
    public static void main(final String[] args) throws IOException, URISyntaxException {
        final List<TrackerDefinition> trackerDefinitions = TrackerCsvReader.readTrackerInfo();

        for (final TrackerDefinition trackerDefinition : trackerDefinitions) {
            LOGGER.info("{}", trackerDefinition.trackerName());
            final ChromeDriver driver = createDriver();

            try {
                takeScreenshotOfTrackerProfilePage(driver, trackerDefinition);
            } catch (final IOException e) {
                driver.quit();
                throw e;
            }
        }
    }

    private static void takeScreenshotOfTrackerProfilePage(final ChromeDriver driver, final TrackerDefinition trackerDefinition) throws IOException {
        LOGGER.info("\t- Opening login page at '{}'", trackerDefinition.loginLink());
        final TrackerHandler trackerHandler = TrackerHandlerFactory.getHandler(trackerDefinition.trackerCode(), driver);

        trackerHandler.openLoginPage(trackerDefinition);
        LOGGER.info("\t- Logging in as '{}'", trackerDefinition.username());
        trackerHandler.login(trackerDefinition);

        LOGGER.info("\t- Logged in, redirecting to '{}'", trackerDefinition.profilePage());
        trackerHandler.openProfilePage(trackerDefinition);

        if (trackerHandler.canCookieBannerBeCleared()) {
            LOGGER.info("\t- Cookie banner has been cleared");
        }

        final Collection<WebElement> elementsToBeMasked = trackerHandler.getElementsToBeMasked();
        for (final WebElement elementToBeMasked : elementsToBeMasked) {
            ScriptExecutor.maskInnerTextOfElement(driver, elementToBeMasked);
        }
        if (!elementsToBeMasked.isEmpty()) {
            final String plural = elementsToBeMasked.size() == 1 ? "" : "s";
            LOGGER.info("\t- Masked the text of '{}' element{}", elementsToBeMasked.size(), plural);
        }

        final File screenshot = ScreenshotTaker.takeScreenshot(driver, trackerDefinition.trackerName(), OUTPUT_DIRECTORY_PATH, PREVIEW_SCREENSHOT);
        LOGGER.info("\t- Screenshot saved at: {}", screenshot.getAbsolutePath());
        LOGGER.info("");
    }

    private static ChromeDriver createDriver() {
        final ChromeOptions chromeOptions = new ChromeOptions();
        chromeOptions.addArguments("--headless=new");
        return new ChromeDriver(chromeOptions);
    }
}
