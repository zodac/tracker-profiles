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
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import net.zodac.tracker.framework.ExecutionCancellation;
import net.zodac.tracker.framework.TrackerCredential;
import net.zodac.tracker.framework.TrackerHandlerFactory;
import net.zodac.tracker.framework.config.Configuration;
import net.zodac.tracker.framework.config.ExistingScreenshotAction;
import net.zodac.tracker.framework.exception.CancelledInputException;
import net.zodac.tracker.framework.exception.CloudflareVerificationException;
import net.zodac.tracker.framework.exception.NoUserInputException;
import net.zodac.tracker.framework.exception.ScreenshotWriteError;
import net.zodac.tracker.framework.exception.TrackerUnavailableException;
import net.zodac.tracker.framework.exception.TranslationException;
import net.zodac.tracker.framework.progress.TrackerLogContext;
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

    private ProfileScreenshotExecutor() {

    }

    /**
     * Attempts to take a screenshot for the given {@link TrackerCredential}. The tracker name, attempt and {@link TrackerStep} are set in the logging
     * context (see {@link TrackerLogContext}), so that each log message can be attributed to them in the web UI.
     *
     * @param trackerCredential details of the tracker to screenshot
     * @param executionListener the {@link ExecutionListener} to notify as each {@link TrackerStep} completes
     * @return {@code true} if screenshot was successful
     */
    static boolean takeScreenshot(final TrackerCredential trackerCredential, final ExecutionListener executionListener) {
        TrackerLogContext.startTracker(trackerCredential.name());

        try {
            final long startNanos = System.nanoTime();
            final boolean result = takeScreenshotWithAttempts(trackerCredential, executionListener);
            printTrackerExecutionTime(startNanos);
            return result;
        } finally {
            TrackerLogContext.clear();
        }
    }

    private static boolean takeScreenshotWithAttempts(final TrackerCredential trackerCredential, final ExecutionListener executionListener) {
        // May be overwritten during execution with updated count
        // Must be defined at this level so it can retain successful steps through retries
        final Set<TrackerStep> successfulSteps = EnumSet.noneOf(TrackerStep.class);

        // The maximum is read before each attempt, since it can be changed in the web UI during an execution
        for (int attempt = FIRST_ATTEMPT; attempt <= Configuration.get().numberOfScreenshotAttempts(); attempt++) {
            ExecutionCancellation.throwIfCancelled();
            executionListener.trackerAttemptStarted(trackerCredential.name(), attempt);
            TrackerLogContext.startAttempt(attempt);
            final Set<TrackerStep> screenshotResult = screenshotTracker(trackerCredential, executionListener, successfulSteps);
            TrackerLogContext.endAttempt();

            // A failure caused by cancelling (such as the browser being quit) must not be retried
            ExecutionCancellation.throwIfCancelled();
            if (screenshotResult.size() == TrackerStep.NUMBER_OF_STEPS) { // All steps were successful
                LOGGER.trace("Successfully screenshot '{}' on attempt #{}", trackerCredential.name(), attempt);
                clearErrorScreenshots(trackerCredential.name());
                return true;
            }
        }

        LOGGER.debug("All {} attempts exhausted", Configuration.get().numberOfScreenshotAttempts());
        return false;
    }

    private static Set<TrackerStep> screenshotTracker(final TrackerCredential trackerCredential, final ExecutionListener executionListener,
        final Set<TrackerStep> successfulSteps) {
        AbstractTrackerHandler trackerHandler = null;
        try { // NOPMD: UseTryWithResources - need access to the trackerHandler to take a screenshot on error
            trackerHandler = TrackerHandlerFactory.getHandler(trackerCredential.name());
            screenshotProfile(trackerHandler, trackerCredential, executionListener, successfulSteps);
            return successfulSteps;
        } catch (final CancelledInputException e) {
            LOGGER.debug("User skipped the manual input", e);
            LOGGER.warn("User skipped the manual input");
        } catch (final CloudflareVerificationException e) {
            screenshotOnError(trackerHandler, trackerCredential.name());
            LOGGER.debug("Unable to pass Cloudflare verification", e);
            LOGGER.warn("{}", e.getMessage());
        } catch (final NoSuchElementException e) {
            LOGGER.debug("No tracker handler implementation found", e);
            LOGGER.warn("No tracker handler implementation found");
        } catch (final NoUserInputException e) {
            final String errorMessage = e.getCause() == null ? e.getMessage() : e.getCause().getMessage();
            LOGGER.debug("User provided no manual input", e);
            LOGGER.warn("User provided no manual input: {}", errorMessage);
        } catch (final ScreenshotWriteError e) {
            LOGGER.debug("Error writing screenshot", e);
            LOGGER.warn("Error writing screenshot");
        } catch (final TimeoutException e) {
            screenshotOnError(trackerHandler, trackerCredential.name());
            logTimeoutException(e);
        } catch (final TrackerUnavailableException e) {
            LOGGER.debug("Unable to connect to the tracker", e);
            LOGGER.warn("Unable to connect to the tracker");
        } catch (final TranslationException e) {
            LOGGER.debug("Unable to translate the page to English", e);
            LOGGER.warn("Unable to translate the page to English: {}", e.getMessage());
        } catch (final StaleElementReferenceException e) {
            LOGGER.debug("Page element became stale during interaction", e);
            LOGGER.warn("Page element became stale during interaction");
        } catch (final NoSuchSessionException | NoSuchWindowException | UnreachableBrowserException e) {
            logBrowserUnavailable(e);
        } catch (final Exception e) {
            handleUnexpectedError(trackerHandler, trackerCredential.name(), e);
        } finally {
            if (trackerHandler != null) {
                trackerHandler.close();
            }
        }

        return successfulSteps;
    }

    private static void logTimeoutException(final TimeoutException e) {
        LOGGER.debug("Timed out waiting to find required element", e);

        final String cleanedErrorMessage = StringUtils.firstLine(e.getMessage());
        if (cleanedErrorMessage.isEmpty()) {
            LOGGER.warn("Timed out waiting to find required element");
        } else {
            LOGGER.warn("Timed out waiting to find required element: {}", cleanedErrorMessage);
        }
    }

    // The directory itself is created by ScreenshotDirectories and is intentionally not deleted on clean-up
    private static void clearErrorScreenshots(final String trackerName) {
        final File errorsDirectory = ScreenshotDirectories.errors().toFile();
        final File[] errorScreenshots = errorsDirectory.listFiles((_, name) -> name.startsWith(trackerName));
        if (errorScreenshots == null) {
            return;
        }

        for (final File errorScreenshot : errorScreenshots) {
            try {
                Files.delete(errorScreenshot.toPath());
                LOGGER.debug("Deleted error screenshot after successful retry: [{}]", errorScreenshot.getAbsolutePath());
            } catch (final IOException e) {
                LOGGER.warn("Failed to delete error screenshot: [{}]", errorScreenshot.getAbsolutePath(), e);
            }
        }
    }

    private static void logBrowserUnavailable(final Exception e) {
        LOGGER.debug("Browser became unavailable (cancelled by user, or browser crash)", e);
        if (!ExecutionCancellation.isCancelled()) {
            LOGGER.warn("Browser became unavailable (cancelled by user, or browser crash)");
        }
    }

    private static void handleUnexpectedError(final @Nullable AbstractTrackerHandler trackerHandler, final String trackerName, final Exception e) {
        if (ExecutionCancellation.isCancelled()) {
            // Expected while cancelling (the browser is quit, or no new browser is available), so not reported as an error
            LOGGER.debug("Execution cancelled while taking screenshot", e);
            return;
        }

        screenshotOnError(trackerHandler, trackerName);
        LOGGER.debug("Unexpected error taking screenshot", e);

        final String cleanedErrorMessage = StringUtils.firstLine(e.getMessage());
        if (cleanedErrorMessage.isEmpty()) {
            LOGGER.warn("Unexpected {} taking screenshot", e.getClass().getSimpleName());
        } else {
            LOGGER.warn("Unexpected {} taking screenshot: {}", e.getClass().getSimpleName(), cleanedErrorMessage);
        }
    }

    private static void screenshotOnError(final @Nullable AbstractTrackerHandler trackerHandler, final String trackerName) {
        if (!Configuration.get().takeScreenshotOnError()) {
            LOGGER.trace("Error occurred, but screenshots for errors are not enabled");
            return;
        }

        if (trackerHandler == null) {
            LOGGER.warn("Unable to take failure screenshot, trackerHandler is unexpectedly null");
            return;
        }

        try {
            ScreenshotDirectories.createErrorsDirectory();
            LOGGER.trace("Taking failure screenshot");
            final File screenshot =
                ScreenshotTaker.takeScreenshot(trackerHandler.driver(), ScreenshotDirectories.errors(), trackerName, false, 0).get();
            LOGGER.warn("Failure screenshot saved at: [{}]", screenshot.getAbsolutePath());
        } catch (final ExecutionException | IOException e) {
            LOGGER.debug("Unable to take failure screenshot", e);
            LOGGER.warn("Unable to take failure screenshot: {}", e.getMessage());
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.debug("Interrupted while taking failure screenshot", e);
            LOGGER.warn("Interrupted while taking failure screenshot: {}", e.getMessage());
        }
    }

    private static void screenshotProfile(final AbstractTrackerHandler trackerHandler, final TrackerCredential trackerCredential,
        final ExecutionListener executionListener, final Set<TrackerStep> successfulSteps) {
        LOGGER.trace("Starting to take screenshot of profile");
        final List<RedactionType> redactionsToExecute = redactionTypesToExecute(trackerCredential.name(), Configuration.get().redactionTypes());
        if (redactionsToExecute.isEmpty()) {
            LOGGER.warn("Screenshots already exist for all redaction types, skipping");
            for (final TrackerStep trackerStep : TrackerStep.values()) {
                completeStep(trackerCredential.name(), trackerStep, executionListener, successfulSteps);
            }
            return;
        }

        if (redactionsToExecute.size() != Configuration.get().redactionTypes().size()) {
            LOGGER.warn("Some screenshots already exist, only executing: {}", redactionsToExecute);
        }

        TrackerLogContext.startStep(TrackerStep.OPEN_TRACKER);
        trackerHandler.openTracker();
        trackerHandler.navigateToLoginPage();
        completeStep(trackerCredential.name(), TrackerStep.OPEN_TRACKER, executionListener, successfulSteps);

        TrackerLogContext.startStep(TrackerStep.LOGIN);
        LOGGER.info("Logging in as '{}'", trackerCredential.username());
        trackerHandler.login(trackerCredential.username(), trackerCredential.password(), trackerCredential.name());

        if (trackerHandler instanceof final HasDismissibleElement trackerWithBanner) {
            trackerWithBanner.dismiss();
            LOGGER.info("Banner has been cleared");
        }
        completeStep(trackerCredential.name(), TrackerStep.LOGIN, executionListener, successfulSteps);

        TrackerLogContext.startStep(TrackerStep.OPEN_PROFILE_PAGE);
        trackerHandler.openProfilePage();
        completeStep(trackerCredential.name(), TrackerStep.OPEN_PROFILE_PAGE, executionListener, successfulSteps);

        TrackerLogContext.startStep(TrackerStep.TAKE_SCREENSHOTS);
        final boolean scrollDuringScreenshot = !(trackerHandler instanceof DoesNotScrollDuringScreenshot);

        // If the tracker has no sensitive information, all redaction types produce identical screenshots, so we take a single one
        final List<RedactionType> effectiveRedactions;
        if (RedactionRegistry.hasAnyRedaction(trackerHandler)) {
            effectiveRedactions = redactionsToExecute;
        } else {
            LOGGER.debug("No sensitive information to redact, taking a single screenshot");
            effectiveRedactions = redactionTypesToExecute(trackerCredential.name(), Set.of(RedactionType.NONE));
            if (effectiveRedactions.isEmpty()) {
                LOGGER.warn("Screenshot already exists with no sensitive information, skipping");
            }
        }

        final int numberOfUpdates = updateProfilePage(trackerHandler);
        for (final RedactionType redactionType : effectiveRedactions) {
            takeScreenshotForRedactionType(trackerHandler, trackerCredential, redactionType, scrollDuringScreenshot);
        }
        completeStep(trackerCredential.name(), TrackerStep.TAKE_SCREENSHOTS, executionListener, successfulSteps);

        TrackerLogContext.startStep(TrackerStep.LOGOUT);
        if (numberOfUpdates > 0) {
            LOGGER.debug("{} updates made to profile page, reloading before logout", numberOfUpdates);
        }

        trackerHandler.logout();
        LOGGER.info("Logged out");
        completeStep(trackerCredential.name(), TrackerStep.LOGOUT, executionListener, successfulSteps);
    }

    // A step is only reported the first time it succeeds, since successful steps are retained across attempts
    private static void completeStep(final String trackerName, final TrackerStep trackerStep, final ExecutionListener executionListener,
        final Set<TrackerStep> successfulSteps) {
        if (successfulSteps.add(trackerStep)) {
            executionListener.trackerStepCompleted(trackerName, trackerStep);
        }
    }

    private static void takeScreenshotForRedactionType(final AbstractTrackerHandler trackerHandler, final TrackerCredential trackerCredential,
        final RedactionType redactionType, final boolean scrollDuringScreenshot) {
        if (redactionType != RedactionType.NONE) {
            TrackerLogContext.startSection("%s redaction".formatted(redactionType.formattedName()));
        }

        try {
            takeScreenshotWithRedaction(trackerHandler, trackerCredential, redactionType, scrollDuringScreenshot);
        } finally {
            TrackerLogContext.endSection();
        }
    }

    private static void takeScreenshotWithRedaction(final AbstractTrackerHandler trackerHandler, final TrackerCredential trackerCredential,
        final RedactionType redactionType, final boolean scrollDuringScreenshot) {
        final String baseName = screenshotBaseName(trackerCredential.name(), redactionType);

        final Redactor redactor = performRedaction(trackerHandler, redactionType);
        if (redactor != null) {
            LOGGER.trace("Redaction performed by: {}", redactor);
        }

        trackerHandler.actionBeforeScreenshot();
        final Future<File> pendingWrite = ScreenshotTaker.takeScreenshot(trackerHandler.driver(), Configuration.get().outputDirectory(), baseName,
            scrollDuringScreenshot, screenshotIndex(baseName));
        trackerHandler.actionAfterScreenshot();

        try {
            LOGGER.info("Screenshot saved at: [{}]", pendingWrite.get().getAbsolutePath());
        } catch (final ExecutionException e) {
            throw new ScreenshotWriteError(baseName, e);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ScreenshotWriteError(baseName, e);
        }

        if (redactor != null) {
            LOGGER.debug("Undoing redaction");
            redactor.undoRedaction();
        }
    }

    // Perform modifications to the user profile page before redaction so redaction positions are computed against the settled layout
    private static int updateProfilePage(final AbstractTrackerHandler trackerHandler) {
        LOGGER.info("Performing updates to profile page, if needed");
        int counter = 0;

        if (Configuration.get().enableTranslationToEnglish() && trackerHandler instanceof final NeedsExplicitTranslation trackerNeedsTranslation) {
            LOGGER.info("Translating profile page to English");
            trackerNeedsTranslation.translatePageToEnglish();
            counter++;
        }

        if (trackerHandler instanceof final HasFixedHeader trackerWithFixedHeader) {
            LOGGER.debug("Unfixing header");
            trackerWithFixedHeader.unfixHeaders(trackerHandler.driver(), trackerWithFixedHeader.headerSelectors());
            LOGGER.info("Header has been updated to not be fixed");
            counter++;
        }

        if (trackerHandler instanceof final HasFixedSidebar trackerWithFixedSidebar) {
            LOGGER.debug("Unfixing sidebar");
            trackerWithFixedSidebar.unfixSidebar(trackerHandler.driver());
            LOGGER.info("Sidebar has been updated to not be fixed");
            counter++;
        }

        if (trackerHandler instanceof final HasJumpButtons trackerWithJumpButtons) {
            LOGGER.debug("Hiding jump to top/bottom buttons");
            trackerWithJumpButtons.hideJumpButtons(trackerHandler.driver(), trackerWithJumpButtons.jumpButtonSelectors());
            LOGGER.info("Top/bottom jump buttons have been hidden");
            counter++;
        }

        return counter;
    }

    @Nullable
    private static Redactor performRedaction(final AbstractTrackerHandler trackerHandler, final RedactionType redactionType) {
        if (redactionType == RedactionType.NONE) {
            LOGGER.debug("Not redacting content");
            return null;
        }

        if (!RedactionRegistry.hasAnyRedaction(trackerHandler)) {
            LOGGER.debug("Nothing to redact");
            return null;
        }

        final Redactor redactor = RedactorDelegator.create(trackerHandler.driver(), redactionType);
        LOGGER.info("Redacting elements with sensitive information");

        final int numberOfRedactedElements = trackerHandler.redactElements(redactor);
        if (numberOfRedactedElements == 0) {
            LOGGER.warn("Unexpectedly found nothing to redact");
        } else {
            LOGGER.info("Redacted the text of {} element{}", numberOfRedactedElements, StringUtils.pluralise(numberOfRedactedElements));
        }
        return redactor;
    }

    private static List<RedactionType> redactionTypesToExecute(final String trackerName, final Set<RedactionType> redactionTypes) {
        if (Configuration.get().existingScreenshotAction() != ExistingScreenshotAction.SKIP) {
            return redactionTypes.stream().toList();
        }

        // If ExistingScreenshotAction == SKIP, we need to verify if any screenshots have already been taken and exclude those from being taken again
        final List<RedactionType> typesToProcess = new ArrayList<>();
        for (final RedactionType type : redactionTypes) {
            final String baseName = screenshotBaseName(trackerName, type);
            if (ScreenshotTaker.howManyScreenshotsAlreadyExist(baseName, Configuration.get().outputDirectory()) == 0) {
                typesToProcess.add(type);
            } else {
                LOGGER.debug("Screenshot already exists for '{}', skipping", baseName);
            }
        }
        return typesToProcess;
    }

    private static int screenshotIndex(final String baseName) {
        return Configuration.get().existingScreenshotAction() == ExistingScreenshotAction.CREATE_ANOTHER
            ? ScreenshotTaker.howManyScreenshotsAlreadyExist(baseName, Configuration.get().outputDirectory())
            : 0;
    }

    private static String screenshotBaseName(final String trackerName, final RedactionType redactionType) {
        return redactionType == RedactionType.NONE ? trackerName : (trackerName + "_" + redactionType.formattedName());
    }

    private static void printTrackerExecutionTime(final long startNanos) {
        final long elapsedNanos = System.nanoTime() - startNanos;
        LOGGER.debug("Execution time: {}", TimingUtils.toNaturalTime(elapsedNanos));
    }
}
