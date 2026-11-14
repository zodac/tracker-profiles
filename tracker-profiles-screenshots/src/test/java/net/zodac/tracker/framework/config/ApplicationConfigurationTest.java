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

import java.time.Duration;
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
            "BROWSER_RESOLUTION", "1920x1080"
        ));

        assertThat(applicationConfiguration.numberOfParallelThreads())
            .isEqualTo(7);
        assertThat(applicationConfiguration.redactionTypes())
            .containsExactly(RedactionType.BLUR, RedactionType.BOX);
        assertThat(applicationConfiguration.trackerExecutionOrder())
            .isEqualTo(Set.of(TrackerType.MANUAL));
        assertThat(applicationConfiguration.browserDimensions())
            .isEqualTo("1920,1080");
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
        final Map<String, String> overrides = Map.of("BROWSER_RESOLUTION", "1920x1050");

        assertThatThrownBy(() -> ApplicationConfiguration.load(overrides))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("1920x1050");
    }

    @Test
    void givenZeroInputTimeout_whenLoad_thenInputTimeoutIsDisabled() {
        final ApplicationConfiguration applicationConfiguration = ApplicationConfiguration.load(Map.of("INPUT_TIMEOUT_SECONDS", "0"));

        assertThat(applicationConfiguration.inputTimeoutEnabled())
            .isFalse();
    }

    @Test
    void givenPositiveInputTimeout_whenLoad_thenInputTimeoutIsEnabled() {
        final ApplicationConfiguration applicationConfiguration = ApplicationConfiguration.load(Map.of("INPUT_TIMEOUT_SECONDS", "120"));

        assertThat(applicationConfiguration.inputTimeoutEnabled())
            .isTrue();
        assertThat(applicationConfiguration.inputTimeoutDuration())
            .isEqualTo(Duration.ofSeconds(120L));
    }

    @Test
    void givenNegativeInputTimeout_whenLoad_thenExceptionIsThrown() {
        final Map<String, String> overrides = Map.of("INPUT_TIMEOUT_SECONDS", "-1");

        assertThatThrownBy(() -> ApplicationConfiguration.load(overrides))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("INPUT_TIMEOUT_SECONDS");
    }

    @Test
    void givenEveryOptionAtItsDefaultValue_whenLoad_thenConfigurationIsValid() {
        final Map<String, String> defaults = new HashMap<>();
        for (final ConfigurationOption option : ConfigurationOption.ALL_VALUES) {
            defaults.put(option.name(), option.defaultValue());
        }

        final ApplicationConfiguration applicationConfiguration = ApplicationConfiguration.load(defaults);

        assertThat(applicationConfiguration.webUiPort())
            .isEqualTo(8080);
        assertThat(applicationConfiguration.browserDimensions())
            .isEqualTo("1680,1050");
        assertThat(applicationConfiguration.inputTimeoutEnabled())
            .isFalse();
    }
}
