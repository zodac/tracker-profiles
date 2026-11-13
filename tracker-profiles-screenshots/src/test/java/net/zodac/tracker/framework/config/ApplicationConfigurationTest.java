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

package net.zodac.tracker.framework.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import net.zodac.tracker.framework.TrackerType;
import net.zodac.tracker.framework.redactor.RedactionType;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ApplicationConfiguration}.
 */
class ApplicationConfigurationTest {

    @Test
    void givenOverrides_whenLoad_thenOverridesTakePrecedence() {
        final ApplicationConfiguration applicationConfiguration = ApplicationConfiguration.load(Map.of(
            "NUMBER_OF_PARALLEL_THREADS", "7",
            "REDACTION_TYPE", "BLUR,BOX",
            "TRACKER_EXECUTION_ORDER", "MANUAL",
            "TRACKER_INPUT_FILE_PATH", "/data/trackers.csv",
            "PROGRESS_BAR_COMPLETE_CHARACTER", "#"
        ));

        assertThat(applicationConfiguration.numberOfParallelThreads())
            .isEqualTo(7);
        assertThat(applicationConfiguration.redactionTypes())
            .containsExactly(RedactionType.BLUR, RedactionType.BOX);
        assertThat(applicationConfiguration.trackerExecutionOrder())
            .isEqualTo(Set.of(TrackerType.MANUAL));
        assertThat(applicationConfiguration.trackerInputFilePath())
            .isEqualTo(Path.of("/data/trackers.csv"));
        assertThat(applicationConfiguration.progressBarCompleteCharacter())
            .isEqualTo('#');
    }

    @Test
    void givenInvalidIntegerOverride_whenLoad_thenExceptionIsThrown() {
        final Map<String, String> overrides = Map.of("NUMBER_OF_PARALLEL_THREADS", "33");

        assertThatThrownBy(() -> ApplicationConfiguration.load(overrides))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("NUMBER_OF_PARALLEL_THREADS");
    }

    @Test
    void givenUnsupportedResolutionOverride_whenLoad_thenExceptionIsThrown() {
        final Map<String, String> overrides = Map.of("BROWSER_WIDTH", "1920", "BROWSER_HEIGHT", "1050");

        assertThatThrownBy(() -> ApplicationConfiguration.load(overrides))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("1920x1050");
    }

    @Test
    void givenMultipleCharacterOverride_whenLoad_thenExceptionIsThrown() {
        final Map<String, String> overrides = Map.of("PROGRESS_BAR_COMPLETE_CHARACTER", "ab");

        assertThatThrownBy(() -> ApplicationConfiguration.load(overrides))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("PROGRESS_BAR_COMPLETE_CHARACTER");
    }

    @Test
    void givenEmptyCsvCommentSymbolOverride_whenLoad_thenExceptionIsThrown() {
        final Map<String, String> overrides = Map.of("CSV_COMMENT_SYMBOL", "");

        assertThatThrownBy(() -> ApplicationConfiguration.load(overrides))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("CSV_COMMENT_SYMBOL");
    }

    @Test
    void givenEveryOptionAtItsDefaultValue_whenLoad_thenConfigurationIsValid() {
        final Map<String, String> defaults = new HashMap<>();
        for (final ConfigurationOption option : ConfigurationOption.ALL_VALUES) {
            defaults.put(option.name(), option.defaultValue());
        }

        final ApplicationConfiguration applicationConfiguration = ApplicationConfiguration.load(defaults);

        assertThat(applicationConfiguration.webUiEnabled())
            .isTrue();
        assertThat(applicationConfiguration.webUiPort())
            .isEqualTo(8080);
        assertThat(applicationConfiguration.browserDimensions())
            .isEqualTo("1680,1050");
    }
}
