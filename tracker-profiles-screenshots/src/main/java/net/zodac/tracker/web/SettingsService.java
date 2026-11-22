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

import java.sql.SQLException;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import net.zodac.tracker.framework.config.ApplicationConfiguration;
import net.zodac.tracker.framework.config.ConfigurationOption;
import net.zodac.tracker.web.storage.ApplicationDatabase;
import org.jspecify.annotations.Nullable;

/**
 * Lists and updates the application settings shown in the web UI. Each setting's value is resolved from (in order of precedence): a value saved in
 * the web UI, the environment variable, then the {@link ConfigurationOption#defaultValue()}.
 *
 * <p>
 * Every saved change is passed to a {@link SavedSettingsListener}, so it can be applied straight away (including to an execution in progress).
 */
public final class SettingsService {

    private final ApplicationDatabase applicationDatabase;
    private final BooleanSupplier executionInProgress;
    private final SavedSettingsListener savedSettingsListener;

    private SettingsService(final ApplicationDatabase applicationDatabase, final BooleanSupplier executionInProgress,
        final SavedSettingsListener savedSettingsListener) {
        this.applicationDatabase = applicationDatabase;
        this.executionInProgress = executionInProgress;
        this.savedSettingsListener = savedSettingsListener;
    }

    /**
     * Creates a {@link SettingsService}.
     *
     * @param applicationDatabase   the {@link ApplicationDatabase} storing settings saved in the web UI
     * @param executionInProgress   whether an execution is in progress, during which settings cannot be changed
     * @param savedSettingsListener the {@link SavedSettingsListener} notified each time settings are saved
     * @return the {@link SettingsService}
     */
    public static SettingsService create(final ApplicationDatabase applicationDatabase, final BooleanSupplier executionInProgress,
        final SavedSettingsListener savedSettingsListener) {
        return new SettingsService(applicationDatabase, executionInProgress, savedSettingsListener);
    }

    /**
     * Lists every {@link ConfigurationOption} with its current value, except a {@link ConfigurationOption#sensitive()} one, which is never shown.
     *
     * @return the {@link SettingView}s
     * @throws SQLException thrown if the saved settings cannot be read
     */
    public List<SettingView> listSettings() throws SQLException {
        final Map<String, String> overrides = applicationDatabase.settings().findAll();
        return ConfigurationOption.ALL_VALUES
            .stream()
            .filter(option -> !option.sensitive())
            .sorted(Comparator.comparing(ConfigurationOption::group))
            .map(option -> SettingView.of(option, overrides.get(option.name())))
            .toList();
    }

    /**
     * Saves the provided values. Every saved value is kept (across restarts) and takes precedence over the environment variable, even if it is
     * the same as the environment variable or default value, until it is removed with {@link #resetSetting(String)}. All values are validated
     * together before any are saved.
     *
     * @param values the values to save, keyed by {@link ConfigurationOption#name()}
     * @return the updated {@link SettingView}s
     * @throws IllegalArgumentException thrown if a setting does not exist, cannot be changed at runtime, or the resulting configuration is invalid
     * @throws IllegalStateException    thrown if an execution is in progress
     * @throws SQLException             thrown if the settings cannot be saved
     */
    public List<SettingView> updateSettings(final Map<String, String> values) throws SQLException {
        checkNoExecutionInProgress();
        final Map<String, String> overrides = new LinkedHashMap<>(applicationDatabase.settings().findAll());

        // Settings saved for an option that no longer exists are dropped
        overrides.keySet().removeIf(name -> ConfigurationOption.find(name) == null);
        for (final Map.Entry<String, String> entry : values.entrySet()) {
            final ConfigurationOption option = findEditableOption(entry.getKey());
            overrides.put(option.name(), entry.getValue() == null ? "" : entry.getValue());
        }

        validateAndSave(overrides);
        return listSettings();
    }

    /**
     * Removes the saved value for a setting, so it follows the environment variable (or default value) again.
     *
     * @param name the {@link ConfigurationOption#name()} to reset
     * @return the updated {@link SettingView}s
     * @throws IllegalArgumentException thrown if a setting does not exist, cannot be changed at runtime, or the resulting configuration is invalid
     * @throws IllegalStateException    thrown if an execution is in progress
     * @throws SQLException             thrown if the settings cannot be saved
     */
    public List<SettingView> resetSetting(final String name) throws SQLException {
        checkNoExecutionInProgress();
        final ConfigurationOption option = findEditableOption(name);
        final Map<String, String> overrides = new LinkedHashMap<>(applicationDatabase.settings().findAll());
        overrides.remove(option.name());
        overrides.keySet().removeIf(savedName -> ConfigurationOption.find(savedName) == null);

        validateAndSave(overrides);
        return listSettings();
    }

    private void checkNoExecutionInProgress() {
        if (executionInProgress.getAsBoolean()) {
            throw new IllegalStateException("Settings cannot be changed while an execution is in progress");
        }
    }

    private void validateAndSave(final Map<String, String> overrides) throws SQLException {
        // Throws IllegalArgumentException if invalid, so nothing is saved
        ApplicationConfiguration.load(overrides);
        applicationDatabase.settings().replaceAll(overrides);
        savedSettingsListener.settingsSaved(Map.copyOf(overrides));
    }

    private static ConfigurationOption findEditableOption(final String name) {
        final ConfigurationOption option = ConfigurationOption.find(name);
        if (option == null) {
            throw new IllegalArgumentException("Unknown setting: '%s'".formatted(name));
        }

        if (!option.editableAtRuntime()) {
            throw new IllegalArgumentException("[%s] Can only be set as an environment variable".formatted(option.name()));
        }
        return option;
    }

    /**
     * A {@link ConfigurationOption} and its current value, as returned to the web UI.
     *
     * @param name               the environment variable name
     * @param group              the label of the {@link ConfigurationOption.Group} the setting is displayed under
     * @param label              the user-facing name of the setting
     * @param type               the {@link ConfigurationOption.OptionType}
     * @param description        the description
     * @param allowedValues      the allowed values, for selection types
     * @param minimum            the smallest valid value, for an integer type
     * @param maximum            the largest valid value, for an integer type
     * @param defaultValue       the default value
     * @param environmentValue   the value set by the environment variable, or {@code null} if not set
     * @param savedValue         the value saved in the web UI, or {@code null} if not set
     * @param value              the value currently in effect
     * @param source             where {@code value} comes from
     * @param changedInWebUi     whether {@code value} was saved in the web UI and differs from the value of the deployment (the environment
     *                           variable, or the default if it is not set)
     * @param editable           whether the setting can be changed in the web UI
     */
    public record SettingView(
        String name,
        String group,
        String label,
        ConfigurationOption.OptionType type,
        String description,
        List<String> allowedValues,
        @Nullable Integer minimum,
        @Nullable Integer maximum,
        String defaultValue,
        @Nullable String environmentValue,
        @Nullable String savedValue,
        String value,
        Source source,
        boolean changedInWebUi,
        boolean editable
    ) {

        /**
         * Creates a {@link SettingView} for a {@link ConfigurationOption}.
         *
         * @param option     the {@link ConfigurationOption}
         * @param savedValue the value saved in the web UI, or {@code null} if not set
         * @return the {@link SettingView}
         */
        static SettingView of(final ConfigurationOption option, final @Nullable String savedValue) {
            final String environmentValue = option.environmentValue().orElse(null);
            final String value;
            final Source source;
            // A setting that can only be set as an environment variable never has a saved value (removed on startup, and never saved)
            if (savedValue != null) {
                value = savedValue;
                source = Source.WEB_UI;
            } else if (environmentValue != null) {
                value = environmentValue;
                source = Source.ENVIRONMENT;
            } else {
                value = option.defaultValue();
                source = Source.DEFAULT;
            }

            return new SettingView(
                option.name(),
                option.group().label(),
                option.label(),
                option.type(),
                option.description(),
                option.allowedValues(),
                minimum(option),
                maximum(option),
                option.defaultValue(),
                environmentValue,
                savedValue,
                value,
                source,
                isChangedInWebUi(option, savedValue, environmentValue),
                option.editableAtRuntime()
            );
        }

        /**
         * Whether a value saved in the web UI differs from the value of the deployment: the environment variable, or the default if it is not set.
         * Values other than strings are compared ignoring case, as environment variables may not use the same case as the allowed values.
         *
         * @param option           the {@link ConfigurationOption}
         * @param savedValue       the value saved in the web UI, or {@code null} if not set
         * @param environmentValue the value set by the environment variable, or {@code null} if not set
         * @return {@code true} if the saved value changes the value of the deployment
         */
        static boolean isChangedInWebUi(final ConfigurationOption option, final @Nullable String savedValue,
            final @Nullable String environmentValue) {
            if (savedValue == null) {
                return false;
            }
            final String deploymentValue = environmentValue == null ? option.defaultValue() : environmentValue;
            return option.type() == ConfigurationOption.OptionType.STRING
                ? !savedValue.equals(deploymentValue)
                : !savedValue.equalsIgnoreCase(deploymentValue);
        }

        private static @Nullable Integer minimum(final ConfigurationOption option) {
            return option.range().map(ConfigurationOption.Range::minimum).orElse(null);
        }

        private static @Nullable Integer maximum(final ConfigurationOption option) {
            return option.range().map(ConfigurationOption.Range::maximum).orElse(null);
        }
    }

    /**
     * Notified each time settings are saved in the web UI.
     */
    @FunctionalInterface
    public interface SavedSettingsListener {

        /**
         * Called after settings have been validated and saved.
         *
         * @param savedSettings every setting saved in the web UI, keyed by environment variable name
         */
        void settingsSaved(Map<String, String> savedSettings);
    }

    /**
     * Where the value of a setting comes from.
     */
    public enum Source {

        /**
         * The {@link ConfigurationOption#defaultValue()}.
         */
        DEFAULT,

        /**
         * The environment variable.
         */
        ENVIRONMENT,

        /**
         * A value saved in the web UI.
         */
        WEB_UI
    }
}
