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

package net.zodac.tracker;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import net.zodac.tracker.framework.Configuration;
import net.zodac.tracker.framework.ConfigurationProperties;
import net.zodac.tracker.framework.TrackerCsvReader;
import net.zodac.tracker.framework.TrackerDefinition;
import net.zodac.tracker.framework.TrackerHandlerFactory;
import net.zodac.tracker.handler.AbstractTrackerHandler;
import net.zodac.tracker.util.ScreenshotTaker;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.chrome.ChromeDriver;

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
     * <p>
     * A new {@link ChromeDriver} is created for each {@link TrackerDefinition}. Once created, the size of the browser window is set to
     * {@link ConfigurationProperties#browserDimensions()}. If {@link ConfigurationProperties#useHeadlessBrowser()} is {@code true}, then the
     * execution will be done in the background. Otherwise, a browser window will open for each tracker, and all UI actions will be visible for
     * debugging.
     *
     * @param args input arguments, unused
     * @throws IOException        thrown on error parsing CSV input file
     * @throws URISyntaxException thrown on error reading CSV input file
     * @see ScreenshotTaker
     */
    public static void main(final String[] args) throws IOException, URISyntaxException {
        final Map<Boolean, Set<TrackerDefinition>> trackersByIsManual = getTrackers();

        // Non-manual trackers
        for (final TrackerDefinition trackerDefinition : trackersByIsManual.getOrDefault(Boolean.FALSE, Set.of())) {
            takeScreenshotPerTracker(trackerDefinition);
        }

        if (CONFIG.includeManualTrackers()) {
            LOGGER.warn("Executing manual trackers, will require user interaction");
            for (final TrackerDefinition trackerDefinition : trackersByIsManual.getOrDefault(Boolean.TRUE, Set.of())) {
                takeScreenshotPerTracker(trackerDefinition);
            }
        }
    }

    private static Map<Boolean, Set<TrackerDefinition>> getTrackers() throws IOException, URISyntaxException {
        final List<TrackerDefinition> trackerDefinitions = TrackerCsvReader.readTrackerInfo();
        final Map<Boolean, Set<TrackerDefinition>> trackersByIsManual = new HashMap<>();

        for (final TrackerDefinition trackerDefinition : trackerDefinitions) {
            if (TrackerHandlerFactory.doesHandlerExist(trackerDefinition.name())) {
                final Set<TrackerDefinition> existingTrackerDefinitionsOfType =
                    trackersByIsManual.getOrDefault(trackerDefinition.manual(), new HashSet<>());
                existingTrackerDefinitionsOfType.add(trackerDefinition);
                trackersByIsManual.put(trackerDefinition.manual(), existingTrackerDefinitionsOfType);
            } else {
                LOGGER.warn("No {} implemented for tracker '{}'", AbstractTrackerHandler.class.getSimpleName(), trackerDefinition.name());
            }
        }

        final String trackersPlural = trackersByIsManual.values().size() == 1 ? "" : "s";
        if (CONFIG.includeManualTrackers()) {
            LOGGER.info("Screenshotting {} tracker{} ({} manual), saving to: [{}]", trackersByIsManual.values().size(), trackersPlural,
                trackersByIsManual.getOrDefault(Boolean.TRUE, Set.of()).size(), CONFIG.outputDirectory().toAbsolutePath());
        } else {
            LOGGER.info("Screenshotting {} tracker{} (ignoring {} manual), saving to: [{}]",
                trackersByIsManual.getOrDefault(Boolean.FALSE, Set.of()).size(), trackersPlural,
                trackersByIsManual.getOrDefault(Boolean.TRUE, Set.of()).size(), CONFIG.outputDirectory().toAbsolutePath());
        }
        return trackersByIsManual;
    }

    private static void takeScreenshotPerTracker(final TrackerDefinition trackerDefinition) throws IOException {
        LOGGER.info("");
        LOGGER.info("{}", trackerDefinition.name());

        try (final AbstractTrackerHandler trackerHandler = TrackerHandlerFactory.getHandler(trackerDefinition.name(), trackerDefinition.manual())) {
            takeScreenshotOfProfilePage(trackerHandler, trackerDefinition);
        } catch (final NoSuchElementException e) {
            LOGGER.warn("\t- No implementation for tracker '{}'", trackerDefinition.name(), e);
        }
    }

    private static void takeScreenshotOfProfilePage(final AbstractTrackerHandler trackerHandler, final TrackerDefinition trackerDefinition)
        throws IOException {
        LOGGER.info("\t- Opening login page at '{}'", trackerDefinition.loginLink());
        trackerHandler.openLoginPage(trackerDefinition);

        LOGGER.info("\t- Logging in as '{}'", trackerDefinition.username());
        trackerHandler.login(trackerDefinition);

        if (trackerHandler.canBannerBeCleared()) {
            LOGGER.info("\t- Banner has been cleared");
        }

        LOGGER.info("\t- Redirecting to user profile page at '{}'", trackerDefinition.profilePage());
        trackerHandler.openProfilePage(trackerDefinition);

        final int numberOfRedactedElements = trackerHandler.redactElements();
        if (numberOfRedactedElements != 0) {
            final String redactedElementsPlural = numberOfRedactedElements == 1 ? "" : "s";
            LOGGER.info("\t- Redacted the text of '{}' element{}", numberOfRedactedElements, redactedElementsPlural);
        }

        final File screenshot = ScreenshotTaker.takeScreenshot(trackerHandler.driver(), trackerDefinition.name(), trackerHandler.zoomLevel());
        LOGGER.info("\t- Screenshot saved at: [{}]", screenshot.getAbsolutePath());

        trackerHandler.logout();
        LOGGER.info("\t- Logged out");
    }
}
