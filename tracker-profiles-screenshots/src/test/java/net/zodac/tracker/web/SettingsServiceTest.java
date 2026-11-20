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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import net.zodac.tracker.framework.config.ConfigurationOption;
import net.zodac.tracker.web.storage.ApplicationDatabase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unit tests for {@link SettingsService}.
 */
class SettingsServiceTest {

    private static final String SECRET_KEY = "test-secret-key-value";

    // Assumes the environment variable is not set when executing tests
    private static final ConfigurationOption OPTION = ConfigurationOption.NUMBER_OF_SCREENSHOT_ATTEMPTS;

    @TempDir
    private Path tempDirectory;

    private final List<Map<String, String>> savedSettings = new ArrayList<>();
    private final AtomicBoolean executionInProgress = new AtomicBoolean();
    private ApplicationDatabase applicationDatabase;
    private SettingsService settingsService;

    @BeforeEach
    void setUp() throws IOException, SQLException {
        applicationDatabase = openDatabase();
        settingsService = SettingsService.create(applicationDatabase, executionInProgress::get, savedSettings::add);
    }

    @Test
    void givenNoSavedSettings_whenListed_thenEveryOptionIsReturnedInGroupOrder() throws SQLException {
        final List<SettingsService.SettingView> settings = settingsService.listSettings();

        assertThat(settings)
            .extracting(SettingsService.SettingView::name)
            .containsExactlyElementsOf(namesInGroupOrder());
        assertThat(settings)
            .extracting(SettingsService.SettingView::group)
            .startsWith(ConfigurationOption.Group.RUN_OPTIONS.label())
            .endsWith(ConfigurationOption.Group.SYSTEM.label());
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
        assertThat(applicationDatabase.settings().findAll())
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
        assertThat(applicationDatabase.settings().findAll())
            .containsEntry(OPTION.name(), OPTION.defaultValue());
    }

    @Test
    void givenSavedValues_whenServiceIsRecreated_thenSavedValuesAreKept() throws IOException, SQLException {
        settingsService.updateSettings(Map.of(OPTION.name(), "4", ConfigurationOption.LOG_LEVEL.name(), "DEBUG"));

        // Simulates restarting the application, by opening the same database file again
        final SettingsService restartedService = SettingsService.create(openDatabase(),
            executionInProgress::get, savedSettings::add);
        final List<SettingsService.SettingView> settings = restartedService.listSettings();

        assertThat(find(settings, OPTION))
            .returns("4", SettingsService.SettingView::value)
            .returns(SettingsService.Source.WEB_UI, SettingsService.SettingView::source);
        assertThat(find(settings, ConfigurationOption.LOG_LEVEL))
            .returns("DEBUG", SettingsService.SettingView::value);
    }

    @Test
    void givenChangedValue_whenUpdated_thenListenerReceivesSavedSettings() throws SQLException {
        settingsService.updateSettings(Map.of(OPTION.name(), "3"));

        assertThat(savedSettings)
            .containsExactly(Map.of(OPTION.name(), "3"));
    }

    @Test
    void givenSavedSettingForRemovedOption_whenUpdated_thenRemovedOptionIsDropped() throws SQLException {
        applicationDatabase.settings().replaceAll(Map.of("PROGRESS_BAR_ENABLED", "false"));

        settingsService.updateSettings(Map.of(OPTION.name(), "3"));

        assertThat(applicationDatabase.settings().findAll())
            .containsExactly(Map.entry(OPTION.name(), "3"));
    }

    @Test
    void givenInvalidValue_whenUpdated_thenListenerIsNotNotified() {
        final Map<String, String> values = Map.of(OPTION.name(), "100");

        assertThatThrownBy(() -> settingsService.updateSettings(values))
            .isInstanceOf(IllegalArgumentException.class);
        assertThat(savedSettings)
            .isEmpty();
    }

    @Test
    void givenSavedValue_whenReset_thenSavedValueIsRemoved() throws SQLException {
        settingsService.updateSettings(Map.of(OPTION.name(), "3"));
        final List<SettingsService.SettingView> settings = settingsService.resetSetting(OPTION.name());

        assertThat(find(settings, OPTION))
            .returns(OPTION.defaultValue(), SettingsService.SettingView::value);
        assertThat(applicationDatabase.settings().findAll())
            .isEmpty();
    }

    @Test
    void givenInvalidValue_whenUpdated_thenExceptionIsThrownAndNothingIsSaved() throws SQLException {
        final Map<String, String> values = Map.of(ConfigurationOption.LOG_LEVEL.name(), "DEBUG", OPTION.name(), "100");

        assertThatThrownBy(() -> settingsService.updateSettings(values))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining(OPTION.name());
        assertThat(applicationDatabase.settings().findAll())
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
    void givenExecutionInProgress_whenUpdated_thenExceptionIsThrownAndNothingIsSaved() throws SQLException {
        executionInProgress.set(true);
        final Map<String, String> values = Map.of(OPTION.name(), "4");

        assertThatThrownBy(() -> settingsService.updateSettings(values))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("execution is in progress");
        assertThat(applicationDatabase.settings().findAll())
            .isEmpty();
        assertThat(savedSettings)
            .isEmpty();
    }

    @Test
    void givenExecutionInProgress_whenReset_thenExceptionIsThrown() throws SQLException {
        settingsService.updateSettings(Map.of(OPTION.name(), "4"));
        executionInProgress.set(true);

        assertThatThrownBy(() -> settingsService.resetSetting(OPTION.name()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("execution is in progress");
        assertThat(applicationDatabase.settings().findAll())
            .containsEntry(OPTION.name(), "4");
    }

    @Test
    void givenSecretKeyOption_whenListed_thenValueIsNeverReturnedAndCannotBeChanged() throws SQLException {
        final SettingsService.SettingView secretKey = find(settingsService.listSettings(), ConfigurationOption.SECRET_KEY);

        assertThat(secretKey)
            .returns("", SettingsService.SettingView::defaultValue)
            .returns(null, SettingsService.SettingView::savedValue)
            .returns(false, SettingsService.SettingView::editable);
        assertThat(secretKey.value())
            .isIn("", "********");
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

    private ApplicationDatabase openDatabase() throws IOException, SQLException {
        return ApplicationDatabase.open(tempDirectory.resolve("test.db"), SECRET_KEY);
    }

    private static List<String> namesInGroupOrder() {
        return ConfigurationOption.ALL_VALUES
            .stream()
            .sorted(Comparator.comparing(ConfigurationOption::group))
            .map(Enum::name)
            .toList();
    }
}
