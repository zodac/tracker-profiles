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

import java.io.IOException;
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
        assertThat(runManager.successfulScreenshots())
            .isEmpty();
    }

    @Test
    void givenSeveralTrackersFinished_whenSuccessfulScreenshotsRetrieved_thenOnlySuccessfulTrackersAreIncludedByName()
        throws IOException, SQLException {
        final RunManager runManager = RunManager.create(openDatabase(), WebLogAppender.install());
        runManager.executionStarted(Map.of(TrackerType.HEADLESS, List.of("Zeta", "Alpha", "Failed")));
        for (final String trackerName : List.of("Zeta", "Alpha", "Failed")) {
            runManager.trackerStarted(TrackerType.HEADLESS, trackerName);
            runManager.trackerScreenshotSaved(trackerName, RedactionType.NONE, tempDirectory.resolve(trackerName + ".png"));
            runManager.trackerCompleted(TrackerType.HEADLESS, trackerName, !"Failed".equals(trackerName));
        }

        assertThat(runManager.successfulScreenshots())
            .extracting(ScreenshotFile::fileName)
            .containsExactly("Alpha.png", "Zeta.png");
        assertThat(runManager.trackerScreenshots("Unknown"))
            .isEmpty();
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
