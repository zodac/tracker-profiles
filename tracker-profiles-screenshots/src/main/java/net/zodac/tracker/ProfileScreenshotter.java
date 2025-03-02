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
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import net.zodac.tracker.framework.Configuration;
import net.zodac.tracker.framework.ConfigurationProperties;
import net.zodac.tracker.framework.DisabledTrackerException;
import net.zodac.tracker.framework.TrackerCsvReader;
import net.zodac.tracker.framework.TrackerDefinition;
import net.zodac.tracker.framework.TrackerHandler;
import net.zodac.tracker.framework.TrackerHandlerFactory;
import net.zodac.tracker.handler.AbstractTrackerHandler;
import net.zodac.tracker.util.DirectoryOpener;
import net.zodac.tracker.util.ScreenshotTaker;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.remote.UnreachableBrowserException;

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
        final File outputDirectory = CONFIG.outputDirectory().toFile();
        if (!outputDirectory.exists()) {
            outputDirectory.mkdirs();
        }

        final Map<Boolean, Set<TrackerDefinition>> trackersByIsManual = getTrackers();
        if (trackersByIsManual.isEmpty()) {
            LOGGER.warn("No trackers selected!");
            return;
        }

        printTrackersInfo(trackersByIsManual);

        // Non-manual trackers
        for (final TrackerDefinition trackerDefinition : trackersByIsManual.getOrDefault(Boolean.FALSE, Set.of())) {
            takeScreenshotPerTracker(trackerDefinition);
        }

        // TODO: Make manual inputs more interactive:
        //   - Allow execution to resume when input is finished rather than waiting a static period of time
        //   - Show timer on web-page (or remove entirely, but have a catch-all timer to kill execution eventually)
        if (CONFIG.includeManualTrackers() && trackersByIsManual.containsKey(Boolean.TRUE)) {
            LOGGER.warn("");
            LOGGER.warn(">>> Executing manual trackers, will require user interaction <<<");
            for (final TrackerDefinition trackerDefinition : trackersByIsManual.getOrDefault(Boolean.TRUE, Set.of())) {
                takeScreenshotPerTracker(trackerDefinition);
            }
        }

        final Path directory = CONFIG.outputDirectory().toAbsolutePath();
        DirectoryOpener.open(directory.toFile());
    }

    private static void printTrackersInfo(final Map<Boolean, Set<TrackerDefinition>> trackersByIsManual) {
        final int numberOfTrackers = trackersByIsManual.values().stream()
            .mapToInt(Set::size)
            .sum();
        final String trackersPlural = numberOfTrackers == 1 ? "" : "s";
        if (CONFIG.includeManualTrackers()) {
            LOGGER.info("Screenshotting {} tracker{} ({} manual), saving to: [{}]", numberOfTrackers, trackersPlural,
                trackersByIsManual.getOrDefault(Boolean.TRUE, Set.of()).size(), CONFIG.outputDirectory().toAbsolutePath());
        } else {
            LOGGER.info("Screenshotting {} tracker{} (ignoring {} manual), saving to: [{}]",
                trackersByIsManual.getOrDefault(Boolean.FALSE, Set.of()).size(), trackersPlural,
                trackersByIsManual.getOrDefault(Boolean.TRUE, Set.of()).size(), CONFIG.outputDirectory().toAbsolutePath());
        }
    }

    private static Map<Boolean, Set<TrackerDefinition>> getTrackers() throws IOException, URISyntaxException {
        final List<TrackerDefinition> trackerDefinitions = TrackerCsvReader.readTrackerInfo();
        final Map<Boolean, Set<TrackerDefinition>> trackersByIsManual = new HashMap<>();

        for (final TrackerDefinition trackerDefinition : trackerDefinitions) {
            final Optional<TrackerHandler> trackerHandler = TrackerHandlerFactory.findMatchingHandler(trackerDefinition.name());

            if (trackerHandler.isPresent()) {
                final boolean isManual = trackerHandler.get().needsManualInput();
                final Set<TrackerDefinition> existingTrackerDefinitionsOfType = trackersByIsManual.getOrDefault(isManual, new TreeSet<>());
                existingTrackerDefinitionsOfType.add(trackerDefinition);
                trackersByIsManual.put(isManual, existingTrackerDefinitionsOfType);
            } else {
                LOGGER.warn("No {} implemented for tracker '{}'", AbstractTrackerHandler.class.getSimpleName(), trackerDefinition.name());
            }
        }

        return trackersByIsManual;
    }

    private static void takeScreenshotPerTracker(final TrackerDefinition trackerDefinition) {
        LOGGER.info("");
        LOGGER.info("[{}]", trackerDefinition.name());

        try (final AbstractTrackerHandler trackerHandler = TrackerHandlerFactory.getHandler(trackerDefinition.name())) {
            takeScreenshotOfProfilePage(trackerHandler, trackerDefinition);
        } catch (final DisabledTrackerException e) {
            LOGGER.debug("\t- Tracker '{}' is disabled: [{}]", trackerDefinition.name(), e.getMessage(), e);
            LOGGER.warn("\t- Tracker '{}' is disabled: [{}]", trackerDefinition.name(), e.getMessage());
        } catch (final NoSuchElementException e) {
            LOGGER.debug("\t- No implementation for tracker '{}'", trackerDefinition.name(), e);
            LOGGER.warn("\t- No implementation for tracker '{}'", trackerDefinition.name());
        } catch (final UnreachableBrowserException e) {
            LOGGER.warn("Browser unavailable, most likely user-cancelled");
            throw e;
        } catch (final Exception e) {
            final String errorMessage = e.getMessage() == null ? "" : e.getMessage().split("\n")[0];
            LOGGER.debug("\t- Error taking screenshot of '{}': {}", trackerDefinition.name(), errorMessage, e);
            LOGGER.warn("\t- Error taking screenshot of '{}': {}", trackerDefinition.name(), errorMessage);
        }
    }

    private static void takeScreenshotOfProfilePage(final AbstractTrackerHandler trackerHandler, final TrackerDefinition trackerDefinition)
        throws IOException {

        LOGGER.info("\t- Opening tracker");
        trackerHandler.openTracker();
        trackerHandler.navigateToLoginPage();

        LOGGER.info("\t- Logging in as '{}'", trackerDefinition.username());
        trackerHandler.login(trackerDefinition);

        if (trackerHandler.canBannerBeCleared()) {
            LOGGER.info("\t- Banner has been cleared");
        }

        LOGGER.info("\t- Opening user profile page");
        trackerHandler.openProfilePage();

        LOGGER.info("\t- Redacting elements with sensitive information");
        final int numberOfRedactedElements = trackerHandler.redactElements();
        if (numberOfRedactedElements != 0) {
            final String redactedElementsPlural = numberOfRedactedElements == 1 ? "" : "s";
            LOGGER.info("\t\t- Redacted the text of {} element{}", numberOfRedactedElements, redactedElementsPlural);
        }

        if (trackerHandler.canDisableFixedHeader()) {
            LOGGER.info("\t- Header has been updated to not be fixed");
        }

        final File screenshot = ScreenshotTaker.takeScreenshot(trackerHandler.driver(), trackerDefinition.name());
        LOGGER.info("\t- Screenshot saved at: [{}]", screenshot.getAbsolutePath());

        trackerHandler.logout();
        LOGGER.info("\t- Logged out");
    }
}
