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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import net.zodac.tracker.framework.config.ConfigurationOption;
import net.zodac.tracker.web.storage.ApplicationDatabase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unit tests for {@link SettingsService}.
 */
class SettingsServiceTest {

    // Assumes the environment variable is not set when executing tests
    private static final ConfigurationOption OPTION = ConfigurationOption.NUMBER_OF_SCREENSHOT_ATTEMPTS;

    @TempDir
    private Path tempDirectory;

    private ApplicationDatabase applicationDatabase;
    private SettingsService settingsService;

    @BeforeEach
    void setUp() throws IOException, SQLException {
        applicationDatabase = ApplicationDatabase.open(tempDirectory.resolve("test.db"));
        settingsService = SettingsService.create(applicationDatabase);
    }

    @Test
    void givenNoSavedSettings_whenListed_thenEveryOptionIsReturned() throws SQLException {
        final List<SettingsService.SettingView> settings = settingsService.listSettings();

        assertThat(settings)
            .extracting(SettingsService.SettingView::name)
            .containsExactlyElementsOf(ConfigurationOption.ALL_VALUES.stream().map(Enum::name).toList());
        assertThat(find(settings, OPTION))
            .returns(OPTION.defaultValue(), SettingsService.SettingView::value)
            .returns(SettingsService.Source.DEFAULT, SettingsService.SettingView::source)
            .returns(false, SettingsService.SettingView::changedFromDefault);
    }

    @Test
    void givenChangedValue_whenUpdated_thenValueIsSavedAndMarkedAsChanged() throws SQLException {
        final List<SettingsService.SettingView> settings = settingsService.updateSettings(Map.of(OPTION.name(), "3"));

        assertThat(find(settings, OPTION))
            .returns("3", SettingsService.SettingView::value)
            .returns(SettingsService.Source.WEB_UI, SettingsService.SettingView::source)
            .returns(true, SettingsService.SettingView::changedFromDefault);
        assertThat(applicationDatabase.findAllSettings())
            .containsEntry(OPTION.name(), "3");
    }

    @Test
    void givenSavedValue_whenUpdatedToDefaultValue_thenValueIsStillSaved() throws SQLException {
        settingsService.updateSettings(Map.of(OPTION.name(), "3"));
        final List<SettingsService.SettingView> settings = settingsService.updateSettings(Map.of(OPTION.name(), OPTION.defaultValue()));

        assertThat(find(settings, OPTION))
            .returns(OPTION.defaultValue(), SettingsService.SettingView::value)
            .returns(SettingsService.Source.WEB_UI, SettingsService.SettingView::source)
            .returns(false, SettingsService.SettingView::changedFromDefault);
        assertThat(applicationDatabase.findAllSettings())
            .containsEntry(OPTION.name(), OPTION.defaultValue());
    }

    @Test
    void givenSavedValues_whenServiceIsRecreated_thenSavedValuesAreKept() throws IOException, SQLException {
        settingsService.updateSettings(Map.of(OPTION.name(), "4", ConfigurationOption.LOG_LEVEL.name(), "DEBUG"));

        // Simulates restarting the application, by opening the same database file again
        final SettingsService restartedService = SettingsService.create(ApplicationDatabase.open(tempDirectory.resolve("test.db")));
        final List<SettingsService.SettingView> settings = restartedService.listSettings();

        assertThat(find(settings, OPTION))
            .returns("4", SettingsService.SettingView::value)
            .returns(SettingsService.Source.WEB_UI, SettingsService.SettingView::source);
        assertThat(find(settings, ConfigurationOption.LOG_LEVEL))
            .returns("DEBUG", SettingsService.SettingView::value);
    }

    @Test
    void givenCsvOption_whenUpdated_thenExceptionIsThrown() {
        final Map<String, String> values = Map.of(ConfigurationOption.TRACKER_INPUT_FILE_PATH.name(), "/data/trackers.csv");

        assertThatThrownBy(() -> settingsService.updateSettings(values))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("environment variable");
    }

    @Test
    void givenSavedValue_whenReset_thenSavedValueIsRemoved() throws SQLException {
        settingsService.updateSettings(Map.of(OPTION.name(), "3"));
        final List<SettingsService.SettingView> settings = settingsService.resetSetting(OPTION.name());

        assertThat(find(settings, OPTION))
            .returns(OPTION.defaultValue(), SettingsService.SettingView::value);
        assertThat(applicationDatabase.findAllSettings())
            .isEmpty();
    }

    @Test
    void givenInvalidValue_whenUpdated_thenExceptionIsThrownAndNothingIsSaved() throws SQLException {
        final Map<String, String> values = Map.of(ConfigurationOption.LOG_LEVEL.name(), "DEBUG", OPTION.name(), "100");

        assertThatThrownBy(() -> settingsService.updateSettings(values))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining(OPTION.name());
        assertThat(applicationDatabase.findAllSettings())
            .isEmpty();
    }

    @Test
    void givenOptionNotEditableAtRuntime_whenUpdated_thenExceptionIsThrown() {
        final Map<String, String> values = Map.of(ConfigurationOption.WEB_UI_PORT.name(), "9000");

        assertThatThrownBy(() -> settingsService.updateSettings(values))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("environment variable");
    }

    @Test
    void givenUnknownOption_whenUpdated_thenExceptionIsThrown() {
        final Map<String, String> values = Map.of("UNKNOWN", "value");

        assertThatThrownBy(() -> settingsService.updateSettings(values))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Unknown setting");
    }

    private static SettingsService.SettingView find(final List<SettingsService.SettingView> settings, final ConfigurationOption option) {
        return settings.stream()
            .filter(setting -> setting.name().equals(option.name()))
            .findAny()
            .orElseThrow();
    }
}
