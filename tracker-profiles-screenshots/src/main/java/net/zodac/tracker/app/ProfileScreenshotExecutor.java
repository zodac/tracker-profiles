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

package net.zodac.tracker.app;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import net.zodac.tracker.framework.TrackerCredential;
import net.zodac.tracker.framework.TrackerHandlerFactory;
import net.zodac.tracker.framework.config.ApplicationConfiguration;
import net.zodac.tracker.framework.config.Configuration;
import net.zodac.tracker.framework.config.ExistingScreenshotAction;
import net.zodac.tracker.framework.exception.CancelledInputException;
import net.zodac.tracker.framework.exception.NoUserInputException;
import net.zodac.tracker.framework.exception.ScreenshotWriteError;
import net.zodac.tracker.framework.exception.TrackerUnavailableException;
import net.zodac.tracker.framework.exception.TranslationException;
import net.zodac.tracker.framework.progress.ProgressBarManager;
import net.zodac.tracker.framework.progress.TrackerStep;
import net.zodac.tracker.framework.redaction.RedactionRegistry;
import net.zodac.tracker.framework.redactor.RedactionType;
import net.zodac.tracker.framework.redactor.Redactor;
import net.zodac.tracker.framework.redactor.RedactorDelegator;
import net.zodac.tracker.handler.AbstractTrackerHandler;
import net.zodac.tracker.handler.definition.DoesNotScrollDuringScreenshot;
import net.zodac.tracker.handler.definition.HasDismissibleElement;
import net.zodac.tracker.handler.definition.HasFixedHeader;
import net.zodac.tracker.handler.definition.HasFixedSidebar;
import net.zodac.tracker.handler.definition.HasJumpButtons;
import net.zodac.tracker.handler.definition.NeedsExplicitTranslation;
import net.zodac.tracker.util.ScreenshotTaker;
import net.zodac.tracker.util.StringUtils;
import net.zodac.tracker.util.TimingUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.ThreadContext;
import org.jspecify.annotations.Nullable;
import org.openqa.selenium.NoSuchSessionException;
import org.openqa.selenium.NoSuchWindowException;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.remote.UnreachableBrowserException;

/**
 * Handles the execution of taking a screenshot for a single tracker.
 */
final class ProfileScreenshotExecutor {

    private static final int FIRST_ATTEMPT = 1;
    private static final Logger LOGGER = LogManager.getLogger();
    private static final ApplicationConfiguration CONFIG = Configuration.get();
    private static final int MAXIMUM_SCREENSHOT_ATTEMPTS = CONFIG.numberOfScreenshotAttempts();

    private ProfileScreenshotExecutor() {

    }

    /**
     * Attempts to take a screenshot for the given {@link TrackerCredential}.
     *
     * @param trackerCredential  details of the tracker to screenshot
     * @param progressBarManager the progress bar manager to tick at each workflow step
     * @param maxLogLength       the maximum length needed for the log entry to log the tracker name
     * @return {@code true} if screenshot was successful
     * @throws RuntimeException thrown if all attempts are exhausted due to a retryable failure with a known cause
     */
    static boolean takeScreenshot(final TrackerCredential trackerCredential, final ProgressBarManager progressBarManager, final int maxLogLength) {
        setUpPerTrackerLogging(trackerCredential, maxLogLength);

        try {
            final long startNanos = System.nanoTime();
            final boolean result = takeScreenshotWithAttempts(trackerCredential, progressBarManager);
            printTrackerExecutionTime(trackerCredential.name(), startNanos);
            return result;
        } finally {
            ThreadContext.clearAll();
        }
    }

    private static void setUpPerTrackerLogging(final TrackerCredential trackerCredential, final int maxLogLength) {
        if (CONFIG.logTrackerName()) {
            LOGGER.trace("Enabling tracker name logging");
            ThreadContext.put("tracker", ("[%-" + maxLogLength + "s] ").formatted(trackerCredential.name()));
        } else {
            LOGGER.info("[{}]", trackerCredential.name());
        }
    }

    private static boolean takeScreenshotWithAttempts(final TrackerCredential trackerCredential, final ProgressBarManager progressBarManager) {
        // May be overwritten during execution with updated count
        // Must be defined at this level so it can retain successful steps through retries
        final Set<TrackerStep> successfulSteps = EnumSet.noneOf(TrackerStep.class);

        try {
            for (int attempt = 1; attempt <= MAXIMUM_SCREENSHOT_ATTEMPTS; attempt++) {
                if (attempt != FIRST_ATTEMPT) {
                    LOGGER.warn("");
                    LOGGER.info("[{}] (attempt {}/{})", trackerCredential.name(), attempt, MAXIMUM_SCREENSHOT_ATTEMPTS);
                }

                final Set<TrackerStep> screenshotResult = screenshotTracker(trackerCredential, progressBarManager, successfulSteps);
                if (screenshotResult.size() == TrackerStep.NUMBER_OF_STEPS) { // All steps were successful
                    LOGGER.trace("Successfully screenshot '{}' on attempt #{}", trackerCredential.name(), attempt);
                    clearErrorScreenshots(trackerCredential.name(), ScreenshotDirectories.ERRORS);
                    return true;
                }
            }

            LOGGER.debug("\t- All {} attempts exhausted for tracker '{}'", MAXIMUM_SCREENSHOT_ATTEMPTS, trackerCredential.name());
            return false;
        } finally {
            final int remaining = TrackerStep.NUMBER_OF_STEPS - successfulSteps.size();
            if (remaining > 0) {
                LOGGER.trace("Advancing progress bar {} missed step{} due to failed execution", remaining, StringUtils.pluralise(remaining));
                progressBarManager.tickMultipleSteps(remaining);
            }
        }
    }

    private static Set<TrackerStep> screenshotTracker(final TrackerCredential trackerCredential, final ProgressBarManager progressBarManager,
                                                      final Set<TrackerStep> successfulSteps) {
        AbstractTrackerHandler trackerHandler = null;
        try { // NOPMD: UseTryWithResources - need access to the trackerHandler to take a screenshot on error
            trackerHandler = TrackerHandlerFactory.getHandler(trackerCredential.name());
            screenshotProfile(trackerHandler, trackerCredential, progressBarManager, successfulSteps);
            return successfulSteps;
        } catch (final CancelledInputException e) {
            LOGGER.debug("\t- User cancelled manual input for tracker '{}'", trackerCredential.name(), e);
            LOGGER.warn("\t- User cancelled manual input for tracker '{}'", trackerCredential.name());
        } catch (final NoSuchElementException e) {
            LOGGER.debug("\t- No implementation for tracker '{}'", trackerCredential.name(), e);
            LOGGER.warn("\t- No implementation for tracker '{}'", trackerCredential.name());
        } catch (final NoUserInputException e) {
            final String errorMessage = e.getCause() == null ? e.getMessage() : e.getCause().getMessage();
            LOGGER.debug("\t- User provided no manual input for tracker '{}'", trackerCredential.name(), e);
            LOGGER.warn("\t- User provided no manual input for tracker '{}': {}", trackerCredential.name(), errorMessage);
        } catch (final ScreenshotWriteError e) {
            LOGGER.debug("\t- Error writing screenshot for tracker '{}'", trackerCredential.name(), e);
            LOGGER.warn("\t- Error writing screenshot for tracker '{}'", trackerCredential.name());
        } catch (final TimeoutException e) {
            screenshotOnError(trackerHandler, trackerCredential.name(), ScreenshotDirectories.ERRORS);
            LOGGER.debug("\t- Timed out waiting to find required element for tracker '{}'", trackerCredential.name(), e);

            final String cleanedErrorMessage = StringUtils.firstLine(e.getMessage());
            if (cleanedErrorMessage.isEmpty()) {
                LOGGER.warn("\t- Timed out waiting to find required element for tracker '{}'", trackerCredential.name());
            } else {
                LOGGER.warn("\t- Timed out waiting to find required element for tracker '{}': {}", trackerCredential.name(), cleanedErrorMessage);
            }
        } catch (final TrackerUnavailableException e) {
            LOGGER.debug("\t- Unable to connect to tracker '{}'", trackerCredential.name(), e);
            LOGGER.warn("\t- Unable to connect to tracker '{}'", trackerCredential.name());
        } catch (final TranslationException e) {
            LOGGER.debug("\t- Unable to translate tracker '{}' to English", trackerCredential.name(), e);
            LOGGER.warn("\t- Unable to translate tracker '{}' to English: {}", trackerCredential.name(), e.getMessage());
        } catch (final NoSuchSessionException | NoSuchWindowException | StaleElementReferenceException | UnreachableBrowserException e) {
            LOGGER.debug("Browser unavailable, most likely user-cancelled", e);
            LOGGER.warn("Browser unavailable, most likely user-cancelled");
        } catch (final Exception e) {
            screenshotOnError(trackerHandler, trackerCredential.name(), ScreenshotDirectories.ERRORS);
            LOGGER.debug("\t- Unexpected error taking screenshot of '{}'", trackerCredential.name(), e);

            final String cleanedErrorMessage = StringUtils.firstLine(e.getMessage());
            if (cleanedErrorMessage.isEmpty()) {
                LOGGER.warn("\t- Unexpected {} taking screenshot of '{}'", e.getClass().getSimpleName(), trackerCredential.name());
            } else {
                LOGGER.warn("\t- Unexpected {} taking screenshot of '{}': {}", e.getClass().getSimpleName(), trackerCredential.name(),
                    cleanedErrorMessage);
            }
        } finally {
            if (trackerHandler != null) {
                trackerHandler.close();
            }
        }

        return successfulSteps;
    }

    // The directory itself is created up-front by prepareErrorDirectories() and is intentionally not deleted on clean-up
    private static void clearErrorScreenshots(final String trackerName, final Path directory) {
        final File errorsDirectory = directory.toFile();
        final File[] errorScreenshots = errorsDirectory.listFiles((_, name) -> name.startsWith(trackerName));
        if (errorScreenshots == null) {
            return;
        }

        for (final File errorScreenshot : errorScreenshots) {
            try {
                Files.delete(errorScreenshot.toPath());
                LOGGER.debug("\t- Deleted error screenshot after successful retry: [{}]", errorScreenshot.getAbsolutePath());
            } catch (final IOException e) {
                LOGGER.warn("\t- Failed to delete error screenshot: [{}]", errorScreenshot.getAbsolutePath(), e);
            }
        }
    }

    private static void screenshotOnError(final @Nullable AbstractTrackerHandler trackerHandler, final String trackerName, final Path directory) {
        if (!CONFIG.takeScreenshotOnError()) {
            LOGGER.trace("Error occurred, but screenshots for errors are not enabled");
            return;
        }

        if (trackerHandler == null) {
            LOGGER.warn("\t\t- Unable to take failure screenshot, trackerHandler is unexpectedly null");
            return;
        }

        try {
            LOGGER.trace("Taking failure screenshot for '{}'", trackerName);
            final File screenshot = ScreenshotTaker.takeScreenshot(trackerHandler.driver(), directory, trackerName, false, 0).get();
            LOGGER.warn("\t\t- Failure screenshot saved at: [{}]", screenshot.getAbsolutePath());
        } catch (final ExecutionException e) {
            LOGGER.debug("\t\t- Unable to take failure screenshot of '{}'", trackerName, e);
            LOGGER.warn("\t\t- Unable to take failure screenshot of '{}': {}", trackerName, e.getMessage());
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.debug("\t\t- Interrupted while taking failure screenshot of '{}'", trackerName, e);
            LOGGER.warn("\t\t- Interrupted while taking failure screenshot of '{}': {}", trackerName, e.getMessage());
        }
    }

    private static void screenshotProfile(final AbstractTrackerHandler trackerHandler, final TrackerCredential trackerCredential,
                                          final ProgressBarManager progressBarManager, final Set<TrackerStep> successfulSteps) {
        LOGGER.trace("\t- Starting to take screenshot of profile");
        final List<RedactionType> redactionsToExecute = redactionTypesToExecute(trackerCredential.name(), CONFIG.redactionTypes());
        if (redactionsToExecute.isEmpty()) {
            LOGGER.warn("\t- Screenshots already exist for all redaction types for tracker '{}', skipping", trackerCredential.name());
            successfulSteps.addAll(EnumSet.allOf(TrackerStep.class));
            return;
        }

        if (redactionsToExecute.size() != CONFIG.redactionTypes().size()) {
            LOGGER.warn("\t- Some screenshots already exist for tracker '{}', only executing: {}", trackerCredential.name(), redactionsToExecute);
        }

        LOGGER.info("\t- Opening tracker");
        trackerHandler.openTracker();
        trackerHandler.navigateToLoginPage(trackerCredential.name());
        if (!successfulSteps.contains(TrackerStep.OPEN_TRACKER)) {
            progressBarManager.tick(TrackerStep.OPEN_TRACKER);
            successfulSteps.add(TrackerStep.OPEN_TRACKER);
        }

        LOGGER.info("\t- Logging in as '{}'", trackerCredential.username());
        trackerHandler.login(trackerCredential.username(), trackerCredential.password(), trackerCredential.name());

        if (trackerHandler instanceof HasDismissibleElement trackerWithBanner) {
            trackerWithBanner.dismiss();
            LOGGER.info("\t- Banner has been cleared");
        }
        if (!successfulSteps.contains(TrackerStep.LOGIN)) {
            progressBarManager.tick(TrackerStep.LOGIN);
            successfulSteps.add(TrackerStep.LOGIN);
        }

        LOGGER.info("\t- Opening user profile page");
        trackerHandler.openProfilePage();
        if (!successfulSteps.contains(TrackerStep.OPEN_PROFILE_PAGE)) {
            progressBarManager.tick(TrackerStep.OPEN_PROFILE_PAGE);
            successfulSteps.add(TrackerStep.OPEN_PROFILE_PAGE);
        }

        final boolean scrollDuringScreenshot = !(trackerHandler instanceof DoesNotScrollDuringScreenshot);

        // If the tracker has no sensitive information, all redaction types produce identical screenshots, so we take a single one
        final List<RedactionType> effectiveRedactions;
        if (RedactionRegistry.hasAnyRedaction(trackerHandler)) {
            effectiveRedactions = redactionsToExecute;
        } else {
            LOGGER.debug("\t- No sensitive information to redact, taking a single screenshot");
            effectiveRedactions = redactionTypesToExecute(trackerCredential.name(), Set.of(RedactionType.NONE));
            if (effectiveRedactions.isEmpty()) {
                LOGGER.warn("\t- Screenshot already exists for tracker '{}' with no sensitive information, skipping", trackerCredential.name());
            }
        }

        updateProfilePage(trackerHandler);
        for (final RedactionType redactionType : effectiveRedactions) {
            takeScreenshotForRedactionType(trackerHandler, trackerCredential, redactionType, scrollDuringScreenshot);
        }
        if (!successfulSteps.contains(TrackerStep.TAKE_SCREENSHOTS)) {
            progressBarManager.tick(TrackerStep.TAKE_SCREENSHOTS);
            successfulSteps.add(TrackerStep.TAKE_SCREENSHOTS);
        }

        trackerHandler.logout();
        LOGGER.info("\t- Logged out");
        if (!successfulSteps.contains(TrackerStep.LOGOUT)) {
            progressBarManager.tick(TrackerStep.LOGOUT);
            successfulSteps.add(TrackerStep.LOGOUT);
        }
    }

    private static void takeScreenshotForRedactionType(final AbstractTrackerHandler trackerHandler, final TrackerCredential trackerCredential,
                                                       final RedactionType redactionType, final boolean scrollDuringScreenshot) {
        LOGGER.info("\t- Redaction: {}", redactionType.formattedName());
        final String baseName = screenshotBaseName(trackerCredential.name(), redactionType);

        final Redactor redactor = performRedaction(trackerHandler, redactionType, trackerCredential.name());

        trackerHandler.actionBeforeScreenshot();
        final Future<File> pendingWrite = ScreenshotTaker.takeScreenshot(trackerHandler.driver(), CONFIG.outputDirectory(), baseName,
            scrollDuringScreenshot, screenshotIndex(baseName));
        trackerHandler.actionAfterScreenshot();

        try {
            LOGGER.info("\t\t- Screenshot saved at: [{}]", pendingWrite.get().getAbsolutePath());
        } catch (final ExecutionException e) {
            throw new ScreenshotWriteError(baseName, e);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ScreenshotWriteError(baseName, e);
        }

        if (redactor != null) {
            LOGGER.debug("\t\t- Undoing redaction");
            redactor.undoRedaction();
        }
    }

    // Perform modifications to the user profile page before redaction so redaction positions are computed against the settled layout
    private static void updateProfilePage(final AbstractTrackerHandler trackerHandler) {
        LOGGER.info("\t\t- Performing updates to profile page, if needed");

        if (CONFIG.enableTranslationToEnglish() && trackerHandler instanceof NeedsExplicitTranslation trackerNeedsTranslation) {
            LOGGER.info("\t\t\t- Translating profile page to English");
            trackerNeedsTranslation.translatePageToEnglish();
        }

        if (trackerHandler instanceof HasFixedHeader trackerWithFixedHeader) {
            LOGGER.debug("\t\t\t- Unfixing header");
            trackerWithFixedHeader.unfixHeaders(trackerHandler.driver(), trackerWithFixedHeader.headerSelectors());
            LOGGER.info("\t\t\t- Header has been updated to not be fixed");
        }

        if (trackerHandler instanceof HasFixedSidebar trackerWithFixedSidebar) {
            LOGGER.debug("\t\t\t- Unfixing sidebar");
            trackerWithFixedSidebar.unfixSidebar(trackerHandler.driver());
            LOGGER.info("\t\t\t- Sidebar has been updated to not be fixed");
        }

        if (trackerHandler instanceof HasJumpButtons trackerWithJumpButtons) {
            LOGGER.debug("\t\t\t- Hiding jump to top/bottom buttons");
            trackerWithJumpButtons.hideJumpButtons(trackerHandler.driver(), trackerWithJumpButtons.jumpButtonSelectors());
            LOGGER.info("\t\t\t- Top/bottom jump buttons have been hidden");
        }
    }

    @Nullable
    private static Redactor performRedaction(final AbstractTrackerHandler trackerHandler, final RedactionType redactionType,
                                             final String trackerName) {
        if (redactionType == RedactionType.NONE) {
            LOGGER.debug("\t\t- Not redacting content");
            return null;
        }

        if (!RedactionRegistry.hasAnyRedaction(trackerHandler)) {
            LOGGER.debug("\t\t- Nothing to redact");
            return null;
        }

        final Redactor redactor = RedactorDelegator.create(trackerHandler.driver(), redactionType);
        LOGGER.info("\t\t- Redacting elements with sensitive information");

        final int numberOfRedactedElements = trackerHandler.redactElements(redactor);
        if (numberOfRedactedElements == 0) {
            screenshotOnError(trackerHandler, trackerName, ScreenshotDirectories.REDACTION_ERRORS);
            LOGGER.warn("\t\t- Unexpectedly found nothing to redact");
        } else {
            clearErrorScreenshots(trackerName, ScreenshotDirectories.REDACTION_ERRORS);
            LOGGER.info("\t\t- Redacted the text of {} element{}", numberOfRedactedElements, StringUtils.pluralise(numberOfRedactedElements));
        }
        return redactor;
    }

    private static List<RedactionType> redactionTypesToExecute(final String trackerName, final Set<RedactionType> redactionTypes) {
        if (CONFIG.existingScreenshotAction() != ExistingScreenshotAction.SKIP) {
            return redactionTypes.stream().toList();
        }

        // If ExistingScreenshotAction == SKIP, we need to verify if any screenshots have already been taken and exclude those from being taken again
        final List<RedactionType> typesToProcess = new ArrayList<>();
        for (final RedactionType type : redactionTypes) {
            final String baseName = screenshotBaseName(trackerName, type);
            if (ScreenshotTaker.howManyScreenshotsAlreadyExist(baseName, CONFIG.outputDirectory()) == 0) {
                typesToProcess.add(type);
            } else {
                LOGGER.debug("\t- Screenshot already exists for '{}', skipping", baseName);
            }
        }
        return typesToProcess;
    }

    private static int screenshotIndex(final String baseName) {
        return CONFIG.existingScreenshotAction() == ExistingScreenshotAction.CREATE_ANOTHER
            ? ScreenshotTaker.howManyScreenshotsAlreadyExist(baseName, CONFIG.outputDirectory())
            : 0;
    }

    private static String screenshotBaseName(final String trackerName, final RedactionType redactionType) {
        return redactionType == RedactionType.NONE ? trackerName : (trackerName + "_" + redactionType.formattedName());
    }

    private static void printTrackerExecutionTime(final String trackerName, final long startNanos) {
        final long elapsedNanos = System.nanoTime() - startNanos;
        LOGGER.debug("\t- Execution time for {}: {}", trackerName, TimingUtils.toNaturalTime(elapsedNanos));
    }
}
