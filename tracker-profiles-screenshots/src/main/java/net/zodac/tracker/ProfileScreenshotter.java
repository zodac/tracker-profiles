/*
 * BSD Zero Clause License
 *
 * Copyright (c) 2024-2025 zodac.net
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
import java.nio.file.Path;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import net.zodac.tracker.framework.ApplicationConfiguration;
import net.zodac.tracker.framework.Configuration;
import net.zodac.tracker.framework.ExitState;
import net.zodac.tracker.framework.TrackerCsvReader;
import net.zodac.tracker.framework.TrackerDefinition;
import net.zodac.tracker.framework.TrackerHandlerFactory;
import net.zodac.tracker.framework.TrackerType;
import net.zodac.tracker.framework.annotation.TrackerHandler;
import net.zodac.tracker.framework.exception.BrowserClosedException;
import net.zodac.tracker.framework.exception.CancelledInputException;
import net.zodac.tracker.framework.exception.DisabledTrackerException;
import net.zodac.tracker.framework.exception.DriverAttachException;
import net.zodac.tracker.framework.exception.NoUserInputException;
import net.zodac.tracker.framework.exception.TranslationException;
import net.zodac.tracker.handler.AbstractTrackerHandler;
import net.zodac.tracker.util.FileOpener;
import net.zodac.tracker.util.ScreenshotTaker;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.NoSuchSessionException;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.remote.UnreachableBrowserException;

/**
 * Main driver class, which takes a screenshot of the profile page of each tracker listed in the
 * {@link ApplicationConfiguration#trackerInputFilePath()} file.
 */
@SuppressWarnings("OverlyLongMethod")
public final class ProfileScreenshotter {

    private static final Logger LOGGER = LogManager.getLogger();
    private static final ApplicationConfiguration CONFIG = Configuration.get();

    private ProfileScreenshotter() {

    }

    /**
     * Parses the {@link ApplicationConfiguration#trackerInputFilePath()} file using {@link TrackerCsvReader}, then iterates through each
     * {@link TrackerDefinition}. For each tracker a {@link AbstractTrackerHandler} is retrieved and used to navigate to the tracker's profile page
     * (after logging in and any other required actions). At this point, any sensitive information is redacted, and then a screenshot is taken by
     * {@link ScreenshotTaker}, then saved in the {@link ApplicationConfiguration#outputDirectory()}.
     *
     * @return the exit code
     * @see ScreenshotTaker
     */
    public static ExitState executeProfileScreenshotter() {
        final Map<TrackerType, Set<TrackerDefinition>> trackersByType = getTrackers();
        // TODO: If a tracker is listed in the CSV, but the category is disabled, it's possible to execute 0 trackers and skip this early exit
        if (trackersByType.isEmpty()) {
            LOGGER.error("No trackers selected!");
            return ExitState.FAILURE;
        }

        final File outputDirectory = CONFIG.outputDirectory().toFile();
        if (!outputDirectory.exists()) {
            LOGGER.trace("Creating output directory: '{}'", outputDirectory);
            outputDirectory.mkdirs();
        }

        printTrackersInfo(trackersByType);
        final Collection<String> successfulTrackers = new TreeSet<>();
        final Collection<String> unsuccessfulTrackers = new TreeSet<>();

        // Execute in the order specified
        for (final TrackerType trackerType : CONFIG.trackerExecutionOrder()) {
            if (!trackerType.isEnabled(trackersByType, CONFIG)) {
                continue;
            }

            LOGGER.info("");
            LOGGER.info(">>> Executing {} trackers <<<", trackerType.toCapitalised());
            for (final TrackerDefinition trackerDefinition : trackersByType.getOrDefault(trackerType, Set.of())) {
                final boolean successfullyTakenScreenshot = isAbleToTakeScreenshot(trackerDefinition);
                if (successfullyTakenScreenshot) {
                    successfulTrackers.add(trackerDefinition.name());
                } else {
                    unsuccessfulTrackers.add(trackerDefinition.name());
                }
            }
        }

        return returnResultSummary(successfulTrackers, unsuccessfulTrackers);
    }

    private static ExitState returnResultSummary(final Collection<String> successfulTrackers, final Collection<String> unsuccessfulTrackers) {
        if (successfulTrackers.isEmpty()) {
            final String trackersPlural = unsuccessfulTrackers.size() == 1 ? "" : "s";
            LOGGER.error("");
            LOGGER.error("All {} selected tracker{} failed:", unsuccessfulTrackers.size(), trackersPlural);
            for (final String unsuccessfulTracker : unsuccessfulTrackers) {
                LOGGER.error("\t- {}", unsuccessfulTracker);
            }
            return ExitState.FAILURE;
        }

        if (CONFIG.openOutputDirectory()) {
            final Path directory = CONFIG.outputDirectory().toAbsolutePath();
            LOGGER.trace("Opening: '{}'", directory);
            try {
                FileOpener.open(directory.toFile());
            } catch (final IOException e) {
                LOGGER.debug("Unable to open '{}'", directory, e);
                LOGGER.warn("Unable to open '{}': {}", directory, e.getMessage());
            }
        }

        if (unsuccessfulTrackers.isEmpty()) {
            final String trackersPlural = successfulTrackers.size() == 1 ? "" : "s";
            LOGGER.info("");
            LOGGER.info("{} tracker{} successfully screenshot", successfulTrackers.size(), trackersPlural);
            return ExitState.SUCCESS;
        } else {
            final String trackersPlural = unsuccessfulTrackers.size() == 1 ? "" : "s";
            LOGGER.warn("");
            LOGGER.warn("Failures for following tracker{}:", trackersPlural);
            for (final String unsuccessfulTracker : unsuccessfulTrackers) {
                LOGGER.warn("\t- {}", unsuccessfulTracker);
            }
            return ExitState.PARTIAL_FAILURE;
        }
    }

    private static void printTrackersInfo(final Map<TrackerType, Set<TrackerDefinition>> trackersByType) {
        final int numberOfTrackers = countAllEnabled(trackersByType);
        final String trackersPlural = numberOfTrackers == 1 ? "" : "s";

        LOGGER.info("Screenshotting {} tracker{}", numberOfTrackers, trackersPlural);

        if (LOGGER.isDebugEnabled()) {
            for (final TrackerType trackerType : CONFIG.trackerExecutionOrder()) {
                trackerType.printSummary(trackersByType, CONFIG);
            }
        }
    }

    private static int countAllEnabled(final Map<TrackerType, Set<TrackerDefinition>> trackersByType) {
        return TrackerType.ALL_VALUES
            .stream()
            .filter(trackerType -> trackerType.isEnabled(trackersByType, CONFIG))
            .mapToInt(trackerType -> trackersByType.getOrDefault(trackerType, Set.of()).size())
            .sum();
    }

    private static Map<TrackerType, Set<TrackerDefinition>> getTrackers() {
        try {
            final List<TrackerDefinition> trackerDefinitions = TrackerCsvReader.readTrackerInfo();
            final Map<TrackerType, Set<TrackerDefinition>> trackersByType = new EnumMap<>(TrackerType.class);

            for (final TrackerDefinition trackerDefinition : trackerDefinitions) {
                final Optional<TrackerHandler> trackerHandler = TrackerHandlerFactory.findMatchingHandler(trackerDefinition.name());

                if (trackerHandler.isPresent()) {
                    final TrackerType trackerType = trackerHandler.get().type();
                    final Set<TrackerDefinition> existingTrackerDefinitionsOfType = trackersByType.getOrDefault(trackerType, new TreeSet<>());
                    existingTrackerDefinitionsOfType.add(trackerDefinition);
                    trackersByType.put(trackerType, existingTrackerDefinitionsOfType);
                } else {
                    LOGGER.warn("No {} implemented for tracker '{}'", AbstractTrackerHandler.class.getSimpleName(), trackerDefinition.name());
                }
            }

            return trackersByType;
        } catch (final IOException e) {
            LOGGER.warn("Unable to read CSV input file", e);
            return Map.of();
        }
    }

    private static boolean isAbleToTakeScreenshot(final TrackerDefinition trackerDefinition) {
        LOGGER.info("");
        LOGGER.info("[{}]", trackerDefinition.name());

        // TODO: Add a retry option
        // TODO: On failure, take a screenshot and add to a subdirectory
        try (final AbstractTrackerHandler trackerHandler = TrackerHandlerFactory.getHandler(trackerDefinition.name())) {
            takeScreenshotOfProfilePage(trackerHandler, trackerDefinition);
            return true;
        } catch (final CancelledInputException e) {
            LOGGER.debug("\t- User cancelled manual input for tracker '{}'", trackerDefinition.name(), e);
            LOGGER.warn("\t- User cancelled manual input for tracker '{}'", trackerDefinition.name());
            return false;
        } catch (final DisabledTrackerException e) {
            LOGGER.debug("\t- Tracker '{}' is disabled: [{}]", trackerDefinition.name(), e.getMessage(), e);
            LOGGER.warn("\t- Tracker '{}' is disabled: [{}]", trackerDefinition.name(), e.getMessage());
            return false;
        } catch (final DriverAttachException e) {
            LOGGER.debug("\t- Unable to attach to Python Selenium web browser for tracker '{}'", trackerDefinition.name(), e);
            LOGGER.warn("\t- Unable to attach to Python Selenium web browser for tracker '{}'", trackerDefinition.name());
            return false;
        } catch (final NoSuchElementException e) {
            LOGGER.debug("\t- No implementation for tracker '{}'", trackerDefinition.name(), e);
            LOGGER.warn("\t- No implementation for tracker '{}'", trackerDefinition.name());
            return false;
        } catch (final NoUserInputException e) {
            LOGGER.debug("\t- User provided no manual input for tracker '{}'", trackerDefinition.name(), e);
            LOGGER.warn("\t- User provided no manual input for tracker '{}'", trackerDefinition.name());
            return false;
        } catch (final TimeoutException e) {
            LOGGER.debug("\t- Timed out waiting to find required element for tracker '{}'", trackerDefinition.name(), e);

            if (e.getMessage() == null) {
                LOGGER.warn("\t- Timed out waiting to find required element for tracker '{}'", trackerDefinition.name());
            } else {
                final String errorMessage = e.getMessage().split("\n")[0];
                LOGGER.warn("\t- Timed out waiting to find required element for tracker '{}': {}", trackerDefinition.name(), errorMessage);
            }

            return false;
        } catch (final TranslationException e) {
            LOGGER.debug("\t- Unable to translate tracker '{}' to English", trackerDefinition.name(), e);
            LOGGER.warn("\t- Unable to translate tracker '{}' to English: {}", trackerDefinition.name(), e.getMessage());
            return false;
        } catch (final NoSuchSessionException | UnreachableBrowserException e) {
            LOGGER.debug("Browser unavailable, most likely user-cancelled", e);
            throw new BrowserClosedException(e);
        } catch (final Exception e) {
            LOGGER.debug("\t- Unexpected error taking screenshot of '{}'", trackerDefinition.name(), e);

            if (e.getMessage() == null) {
                LOGGER.warn("\t- Unexpected error taking screenshot of '{}'", trackerDefinition.name());
            } else {
                final String errorMessage = e.getMessage().split("\n")[0];
                LOGGER.warn("\t- Unexpected error taking screenshot of '{}': {}", trackerDefinition.name(), errorMessage);
            }

            return false;
        }
    }

    private static void takeScreenshotOfProfilePage(final AbstractTrackerHandler trackerHandler, final TrackerDefinition trackerDefinition)
        throws IOException {
        // TODO: If screenshot already exists, skip tracker (based on env var)

        LOGGER.info("\t- Opening tracker");
        trackerHandler.openTracker();
        trackerHandler.navigateToLoginPage(trackerDefinition.name());

        LOGGER.info("\t- Logging in as '{}'", trackerDefinition.username());
        trackerHandler.login(trackerDefinition.username(), trackerDefinition.password(), trackerDefinition.name());

        if (trackerHandler.canBannerBeCleared()) {
            LOGGER.info("\t- Banner has been cleared");
        }

        LOGGER.info("\t- Opening user profile page");
        trackerHandler.openProfilePage();

        // TODO: Take both redacted and non-redacted screenshots?
        if (!trackerHandler.getElementsPotentiallyContainingSensitiveInformation().isEmpty()) {
            LOGGER.info("\t- Redacting elements with sensitive information");
            final int numberOfRedactedElements = trackerHandler.redactElements();
            if (numberOfRedactedElements != 0) {
                final String redactedElementsPlural = numberOfRedactedElements == 1 ? "" : "s";
                LOGGER.info("\t\t- Redacted the text of {} element{}", numberOfRedactedElements, redactedElementsPlural);
            }
        }

        if (trackerHandler.hasFixedHeader()) {
            LOGGER.info("\t- Header has been updated to not be fixed");
        }

        if (CONFIG.enableTranslationToEnglish() && trackerHandler.isNotEnglish(trackerDefinition.username())) {
            LOGGER.info("\t- Profile page has been translated to English");
        }

        final File screenshot = ScreenshotTaker.takeScreenshot(trackerHandler.driver(), trackerDefinition.name());
        LOGGER.info("\t- Screenshot saved at: [{}]", screenshot.getAbsolutePath());

        trackerHandler.logout();
        LOGGER.info("\t- Logged out");
    }
}
