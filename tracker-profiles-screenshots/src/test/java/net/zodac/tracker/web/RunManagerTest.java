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

package net.zodac.tracker.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import net.zodac.tracker.framework.TrackerType;
import net.zodac.tracker.framework.config.Configuration;
import net.zodac.tracker.framework.redactor.RedactionType;
import net.zodac.tracker.web.log.WebLogAppender;
import net.zodac.tracker.web.screenshot.ScreenshotFile;
import net.zodac.tracker.web.storage.ApplicationDatabase;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unit tests for {@link RunManager}.
 */
class RunManagerTest {

    private static final String SECRET_KEY = "test-secret-key-value";

    @TempDir
    private Path tempDirectory;

    @AfterEach
    void tearDown() {
        Configuration.reload(Map.of());
    }

    @Test
    void givenNoExecution_whenCancelled_thenNothingIsCancelled() throws IOException, SQLException {
        final RunManager runManager = RunManager.create(openDatabase(), WebLogAppender.install());

        assertThat(runManager.cancel())
            .isFalse();
        assertThat(runManager.status().state())
            .isEqualTo(RunStatus.State.IDLE);
    }

    @Test
    void givenNoExecution_whenStatusRetrieved_thenNoProgressIsReported() throws IOException, SQLException {
        final RunManager runManager = RunManager.create(openDatabase(), WebLogAppender.install());

        assertThat(runManager.status())
            .returns(0, RunStatus::totalTrackers)
            .returns(List.of(), RunStatus::trackers)
            .returns(List.of(), RunStatus::pendingInputs);
    }

    @Test
    void givenNoExecution_whenSettingsSaved_thenWholeConfigurationIsReloaded() throws IOException, SQLException {
        Configuration.reload(Map.of());
        final RunManager runManager = RunManager.create(openDatabase(), WebLogAppender.install());

        runManager.applySavedSettings(Map.of("NUMBER_OF_SCREENSHOT_ATTEMPTS", "3", "NUMBER_OF_PARALLEL_THREADS", "4"));

        assertThat(Configuration.get().numberOfScreenshotAttempts())
            .isEqualTo(3);
        assertThat(Configuration.get().numberOfParallelThreads())
            .isEqualTo(4);
    }

    @Test
    void givenScreenshotsSaved_whenTrackerSucceeds_thenProfileScreenshotsAreShownWithoutFailureScreenshot() throws IOException, SQLException {
        final RunManager runManager = RunManager.create(openDatabase(), WebLogAppender.install());
        runManager.executionStarted(Map.of(TrackerType.HEADLESS, List.of("Tracker", "Other")));
        runManager.trackerTypeStarted(TrackerType.HEADLESS);
        runManager.trackerStarted(TrackerType.HEADLESS, "Tracker");

        // First attempt fails, second attempt succeeds (replacing the first BOX screenshot)
        runManager.trackerScreenshotSaved("Tracker", RedactionType.BOX, tempDirectory.resolve("Tracker_Box.png"));
        runManager.trackerFailureScreenshotSaved("Tracker", tempDirectory.resolve("errors").resolve("Tracker.png"));
        assertThat(screenshotIds(runManager, "Tracker"))
            .containsExactly("box", "failure");

        runManager.trackerScreenshotSaved("Tracker", RedactionType.BOX, tempDirectory.resolve("Tracker_Box_1.png"));
        runManager.trackerScreenshotSaved("Tracker", RedactionType.BLUR, tempDirectory.resolve("Tracker_Blur.png"));
        runManager.trackerCompleted(TrackerType.HEADLESS, "Tracker", true);

        assertThat(runManager.trackerScreenshots("Tracker"))
            .extracting(ScreenshotFile::fileName)
            .containsExactly("Tracker_Box_1.png", "Tracker_Blur.png");
        assertThat(runManager.status().trackers())
            .filteredOn(tracker -> "Tracker".equals(tracker.name()))
            .singleElement()
            .extracting(RunStatus.TrackerProgress::screenshots)
            .asInstanceOf(InstanceOfAssertFactories.list(RunStatus.Screenshot.class))
            .extracting(RunStatus.Screenshot::label)
            .containsExactly("Box redaction", "Blur redaction");
    }

    @Test
    void givenTrackerFails_whenScreenshotsRetrieved_thenOnlyFailureScreenshotIsShown() throws IOException, SQLException {
        final RunManager runManager = RunManager.create(openDatabase(), WebLogAppender.install());
        runManager.executionStarted(Map.of(TrackerType.HEADLESS, List.of("Tracker")));
        runManager.trackerStarted(TrackerType.HEADLESS, "Tracker");
        runManager.trackerScreenshotSaved("Tracker", RedactionType.NONE, tempDirectory.resolve("Tracker.png"));
        runManager.trackerFailureScreenshotSaved("Tracker", tempDirectory.resolve("errors").resolve("Tracker.png"));
        runManager.trackerCompleted(TrackerType.HEADLESS, "Tracker", false);

        assertThat(screenshotIds(runManager, "Tracker"))
            .containsExactly("failure");
        assertThat(runManager.latestSuccessfulScreenshots())
            .isEmpty();
    }

    @Test
    void givenSeveralTrackersFinished_whenSuccessfulScreenshotsRetrieved_thenOnlySuccessfulTrackersAreIncludedByName()
        throws IOException, SQLException {
        final RunManager runManager = RunManager.create(openDatabase(), WebLogAppender.install());
        runManager.executionStarted(Map.of(TrackerType.HEADLESS, List.of("Zeta", "Alpha", "Failed")));
        for (final String trackerName : List.of("Zeta", "Alpha", "Failed")) {
            runManager.trackerStarted(TrackerType.HEADLESS, trackerName);
            runManager.trackerScreenshotSaved(trackerName, RedactionType.NONE, createScreenshot(trackerName + ".png"));
            runManager.trackerCompleted(TrackerType.HEADLESS, trackerName, !"Failed".equals(trackerName));
        }

        assertThat(runManager.latestSuccessfulScreenshots())
            .extracting(ScreenshotFile::fileName)
            .containsExactly("Alpha.png", "Zeta.png");
        assertThat(runManager.trackerScreenshots("Unknown"))
            .isEmpty();
    }

    @Test
    void givenTrackersSucceededInDifferentExecutions_whenLatestScreenshotsRetrieved_thenOnlyLatestScreenshotOfEachTrackerIsIncluded()
        throws IOException, SQLException {
        final RunManager runManager = RunManager.create(openDatabase(), WebLogAppender.install());
        runManager.executionStarted(Map.of(TrackerType.HEADLESS, List.of("Alpha", "Beta", "Gamma")));
        completeTracker(runManager, "Alpha", "Alpha_1.png", true);
        completeTracker(runManager, "Beta", "Beta_1.png", false);
        completeTracker(runManager, "Gamma", "Gamma_1.png", true);

        // Later executions: Beta now succeeds, Alpha succeeds again, and Gamma fails (keeping the screenshot of its last success)
        completeTracker(runManager, "Beta", "Beta_2.png", true);
        completeTracker(runManager, "Alpha", "Alpha_2.png", true);
        completeTracker(runManager, "Gamma", "Gamma_2.png", false);

        assertThat(runManager.latestSuccessfulScreenshots())
            .extracting(ScreenshotFile::fileName)
            .containsExactly("Alpha_2.png", "Beta_2.png", "Gamma_1.png");
        assertThat(runManager.status())
            .returns(3, RunStatus::downloadableTrackers)
            .returns(3, RunStatus::downloadableScreenshots);
    }

    @Test
    void givenTrackersFailed_whenStatusRetrieved_thenFailedTrackersAreListed() throws IOException, SQLException {
        final RunManager runManager = RunManager.create(openDatabase(), WebLogAppender.install());
        runManager.executionStarted(Map.of(TrackerType.HEADLESS, List.of("Alpha", "Beta", "Gamma")));
        completeTracker(runManager, "Alpha", "Alpha.png", false);
        completeTracker(runManager, "Beta", "Beta.png", true);
        completeTracker(runManager, "Gamma", "Gamma.png", false);

        assertThat(runManager.status().failedTrackers())
            .containsExactly("Alpha", "Gamma");
    }

    @Test
    void givenNoTrackerFailed_whenFailedTrackersStarted_thenNoExecutionIsStarted() throws IOException, SQLException {
        final RunManager runManager = RunManager.create(openDatabase(), WebLogAppender.install());
        runManager.executionStarted(Map.of(TrackerType.HEADLESS, List.of("Alpha")));
        completeTracker(runManager, "Alpha", "Alpha.png", true);

        assertThat(runManager.startFailedTrackers())
            .isFalse();
        assertThat(runManager.status())
            .returns(RunStatus.State.IDLE, RunStatus::state)
            .returns(List.of(), RunStatus::failedTrackers);
    }

    @Test
    void givenSuccessfulScreenshotDeleted_whenStatusRetrieved_thenItIsNotDownloadable() throws IOException, SQLException {
        final RunManager runManager = RunManager.create(openDatabase(), WebLogAppender.install());
        runManager.executionStarted(Map.of(TrackerType.HEADLESS, List.of("Alpha", "Beta")));
        completeTracker(runManager, "Alpha", "Alpha.png", true);
        completeTracker(runManager, "Beta", "Beta.png", true);

        Files.delete(tempDirectory.resolve("Beta.png"));

        assertThat(runManager.latestSuccessfulScreenshots())
            .extracting(ScreenshotFile::fileName)
            .containsExactly("Alpha.png");
        assertThat(runManager.status())
            .returns(1, RunStatus::downloadableTrackers)
            .returns(1, RunStatus::downloadableScreenshots);
    }

    @Test
    void givenTrackersSucceededBeforeRestart_whenRunManagerCreated_thenLatestScreenshotsAreKeptInTheirOrder() throws IOException, SQLException {
        try (final ApplicationDatabase applicationDatabase = openDatabase()) {
            final RunManager runManager = RunManager.create(applicationDatabase, WebLogAppender.install());
            runManager.executionStarted(Map.of(TrackerType.HEADLESS, List.of("Alpha", "Beta")));
            runManager.trackerStarted(TrackerType.HEADLESS, "Alpha");
            runManager.trackerScreenshotSaved("Alpha", RedactionType.BOX, createScreenshot("Alpha_box.png"));
            runManager.trackerScreenshotSaved("Alpha", RedactionType.BLUR, createScreenshot("Alpha_blur.png"));
            // A retry replaces the first screenshot, which keeps its position
            runManager.trackerScreenshotSaved("Alpha", RedactionType.BOX, createScreenshot("Alpha_box_retry.png"));
            runManager.trackerCompleted(TrackerType.HEADLESS, "Alpha", true);
            completeTracker(runManager, "Beta", "Beta.png", false);
        }

        try (final ApplicationDatabase applicationDatabase = openDatabase()) {
            final RunManager runManager = RunManager.create(applicationDatabase, WebLogAppender.install());

            assertThat(runManager.latestScreenshots("alpha"))
                .extracting(ScreenshotFile::id, ScreenshotFile::fileName)
                .containsExactly(tuple("box", "Alpha_box_retry.png"), tuple("blur", "Alpha_blur.png"));
            assertThat(runManager.latestScreenshots("Beta"))
                .isEmpty();
            assertThat(runManager.status())
                .returns(1, RunStatus::downloadableTrackers)
                .returns(2, RunStatus::downloadableScreenshots);
        }
    }

    @Test
    void givenTrackerSucceedsAgain_whenRunManagerCreated_thenOnlyItsLatestScreenshotsAreKept() throws IOException, SQLException {
        try (final ApplicationDatabase applicationDatabase = openDatabase()) {
            final RunManager runManager = RunManager.create(applicationDatabase, WebLogAppender.install());
            runManager.executionStarted(Map.of(TrackerType.HEADLESS, List.of("Alpha")));
            runManager.trackerStarted(TrackerType.HEADLESS, "Alpha");
            runManager.trackerScreenshotSaved("Alpha", RedactionType.BOX, createScreenshot("Alpha_box.png"));
            runManager.trackerScreenshotSaved("Alpha", RedactionType.BLUR, createScreenshot("Alpha_blur.png"));
            runManager.trackerCompleted(TrackerType.HEADLESS, "Alpha", true);

            runManager.executionStarted(Map.of(TrackerType.HEADLESS, List.of("Alpha")));
            completeTracker(runManager, "Alpha", "Alpha_2.png", true);
        }

        try (final ApplicationDatabase applicationDatabase = openDatabase()) {
            assertThat(RunManager.create(applicationDatabase, WebLogAppender.install()).latestScreenshots("Alpha"))
                .extracting(ScreenshotFile::fileName)
                .containsExactly("Alpha_2.png");
        }
    }

    @Test
    void givenTrackerFailsAfterSucceeding_whenRunManagerCreated_thenOnlyScreenshotsOfLatestExecutionAreShown()
        throws IOException, SQLException {
        try (final ApplicationDatabase applicationDatabase = openDatabase()) {
            final RunManager runManager = RunManager.create(applicationDatabase, WebLogAppender.install());
            runManager.executionStarted(Map.of(TrackerType.HEADLESS, List.of("Alpha")));
            completeTracker(runManager, "Alpha", "Alpha.png", true);

            runManager.executionStarted(Map.of(TrackerType.HEADLESS, List.of("Alpha")));
            runManager.trackerStarted(TrackerType.HEADLESS, "Alpha");
            runManager.trackerFailureScreenshotSaved("Alpha", createScreenshot("Alpha_failure.png"));
            runManager.trackerCompleted(TrackerType.HEADLESS, "Alpha", false);
        }

        try (final ApplicationDatabase applicationDatabase = openDatabase()) {
            final RunManager runManager = RunManager.create(applicationDatabase, WebLogAppender.install());

            assertThat(runManager.latestScreenshots("Alpha"))
                .extracting(ScreenshotFile::id, ScreenshotFile::fileName)
                .containsExactly(tuple("failure", "Alpha_failure.png"));
            // The profile screenshots of the latest success are still downloaded together
            assertThat(runManager.latestSuccessfulScreenshots())
                .extracting(ScreenshotFile::fileName)
                .containsExactly("Alpha.png");

            runManager.executionStarted(Map.of(TrackerType.HEADLESS, List.of("Alpha")));
            completeTracker(runManager, "Alpha", "Alpha_2.png", true);
        }

        try (final ApplicationDatabase applicationDatabase = openDatabase()) {
            assertThat(RunManager.create(applicationDatabase, WebLogAppender.install()).latestScreenshots("Alpha"))
                .extracting(ScreenshotFile::fileName)
                .containsExactly("Alpha_2.png");
        }
    }

    @Test
    void givenTrackerFailsWithoutFailureScreenshot_whenLatestScreenshotsRetrieved_thenNoScreenshotsAreShown() throws IOException, SQLException {
        try (final ApplicationDatabase applicationDatabase = openDatabase()) {
            final RunManager runManager = RunManager.create(applicationDatabase, WebLogAppender.install());
            runManager.executionStarted(Map.of(TrackerType.HEADLESS, List.of("Alpha")));
            completeTracker(runManager, "Alpha", "Alpha.png", true);
            runManager.trackerStarted(TrackerType.HEADLESS, "Alpha");
            runManager.trackerCompleted(TrackerType.HEADLESS, "Alpha", false);

            assertThat(runManager.latestScreenshots("Alpha"))
                .isEmpty();
        }

        try (final ApplicationDatabase applicationDatabase = openDatabase()) {
            final RunManager runManager = RunManager.create(applicationDatabase, WebLogAppender.install());
            assertThat(runManager.latestScreenshots("Alpha"))
                .isEmpty();
            assertThat(runManager.latestSuccessfulScreenshots())
                .extracting(ScreenshotFile::fileName)
                .containsExactly("Alpha.png");
        }
    }

    private void completeTracker(final RunManager runManager, final String trackerName, final String fileName, final boolean wasSuccessful)
        throws IOException {
        runManager.trackerStarted(TrackerType.HEADLESS, trackerName);
        runManager.trackerScreenshotSaved(trackerName, RedactionType.NONE, createScreenshot(fileName));
        runManager.trackerCompleted(TrackerType.HEADLESS, trackerName, wasSuccessful);
    }

    // Only screenshots that exist can be downloaded
    private Path createScreenshot(final String fileName) throws IOException {
        return Files.writeString(tempDirectory.resolve(fileName), "");
    }

    private static List<String> screenshotIds(final RunManager runManager, final String trackerName) {
        return runManager.trackerScreenshots(trackerName)
            .stream()
            .map(ScreenshotFile::id)
            .toList();
    }

    private ApplicationDatabase openDatabase() throws IOException, SQLException {
        return ApplicationDatabase.open(tempDirectory.resolve("test.db"), SECRET_KEY);
    }
}
