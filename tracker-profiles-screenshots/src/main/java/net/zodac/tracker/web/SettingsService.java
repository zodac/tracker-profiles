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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.zodac.tracker.framework.config.ApplicationConfiguration;
import net.zodac.tracker.framework.config.ConfigurationOption;
import net.zodac.tracker.web.storage.ApplicationDatabase;
import org.jspecify.annotations.Nullable;

/**
 * Lists and updates the application settings shown in the web UI. Each setting's value is resolved from (in order of precedence): a value saved in
 * the web UI, the environment variable, then the {@link ConfigurationOption#defaultValue()}.
 */
public final class SettingsService {

    private final ApplicationDatabase applicationDatabase;

    private SettingsService(final ApplicationDatabase applicationDatabase) {
        this.applicationDatabase = applicationDatabase;
    }

    /**
     * Creates a {@link SettingsService}.
     *
     * @param applicationDatabase the {@link ApplicationDatabase} storing settings saved in the web UI
     * @return the {@link SettingsService}
     */
    public static SettingsService create(final ApplicationDatabase applicationDatabase) {
        return new SettingsService(applicationDatabase);
    }

    /**
     * Lists every {@link ConfigurationOption} with its current value.
     *
     * @return the {@link SettingView}s
     * @throws SQLException thrown if the saved settings cannot be read
     */
    public List<SettingView> listSettings() throws SQLException {
        final Map<String, String> overrides = applicationDatabase.findAllSettings();
        return ConfigurationOption.ALL_VALUES
            .stream()
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
     * @throws SQLException             thrown if the settings cannot be saved
     */
    public List<SettingView> updateSettings(final Map<String, String> values) throws SQLException {
        final Map<String, String> overrides = new LinkedHashMap<>(applicationDatabase.findAllSettings());

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
     * @throws SQLException             thrown if the settings cannot be saved
     */
    public List<SettingView> resetSetting(final String name) throws SQLException {
        final ConfigurationOption option = findEditableOption(name);
        final Map<String, String> overrides = new LinkedHashMap<>(applicationDatabase.findAllSettings());
        overrides.remove(option.name());

        validateAndSave(overrides);
        return listSettings();
    }

    private void validateAndSave(final Map<String, String> overrides) throws SQLException {
        // Throws IllegalArgumentException if invalid, so nothing is saved
        ApplicationConfiguration.load(overrides);
        applicationDatabase.replaceSettings(overrides);
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
     * @param group              the group the setting is displayed under
     * @param type               the {@link ConfigurationOption.OptionType}
     * @param description        the description
     * @param allowedValues      the allowed values, for selection types
     * @param defaultValue       the default value
     * @param environmentValue   the value set by the environment variable, or {@code null} if not set
     * @param savedValue         the value saved in the web UI, or {@code null} if not set
     * @param value              the value currently in effect
     * @param source             where {@code value} comes from
     * @param changedFromDefault whether {@code value} differs from {@code defaultValue}
     * @param editable           whether the setting can be changed in the web UI
     */
    public record SettingView(
        String name,
        String group,
        ConfigurationOption.OptionType type,
        String description,
        List<String> allowedValues,
        String defaultValue,
        @Nullable String environmentValue,
        @Nullable String savedValue,
        String value,
        Source source,
        boolean changedFromDefault,
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
            if (savedValue != null && option.editableAtRuntime()) {
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
                option.group(),
                option.type(),
                option.description(),
                option.allowedValues(),
                option.defaultValue(),
                environmentValue,
                savedValue,
                value,
                source,
                !value.equals(option.defaultValue()),
                option.editableAtRuntime()
            );
        }
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
