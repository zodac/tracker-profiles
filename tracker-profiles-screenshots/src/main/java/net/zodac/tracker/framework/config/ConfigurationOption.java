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

import java.io.Serial;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import net.zodac.tracker.framework.redactor.RedactionType;
import org.jspecify.annotations.Nullable;

/**
 * Definition of every environment variable that configures the application, used as the single source of truth for each variable's name, default
 * value and description. Used both by {@link ApplicationConfiguration} when loading values, and by the web UI to render each option on the
 * Settings tab, under its {@link Group}.
 */
public enum ConfigurationOption {

    /**
     * The path to the SQLite database used by the web UI.
     */
    DATABASE_PATH(Group.SYSTEM, "Database", "/app/data/tracker-profiles.db", OptionType.STRING, List.of(),
        "The path to the SQLite database file storing tracker credentials and settings changed in the web UI (in its own docker volume)",
        Scope.STARTUP),

    /**
     * Whether to translate non-English trackers.
     */
    ENABLE_TRANSLATION_TO_ENGLISH(Group.RUN_OPTIONS, "Translate to English", "true", OptionType.BOOLEAN, List.of(),
        "Whether to translate non-English trackers to English", Scope.EXECUTION),

    /**
     * The application log level.
     */
    LOG_LEVEL(Group.RUN_OPTIONS, "Log level", "INFO", OptionType.SELECT, List.of("ALL", "TRACE", "DEBUG", "INFO", "WARN", "ERROR", "FATAL", "OFF"),
        "The logging level for application output", Scope.EXECUTION),

    /**
     * The number of parallel threads for each tracker type.
     */
    NUMBER_OF_PARALLEL_THREADS(Group.RUN_OPTIONS, "Parallel browsers", "5", Range.of(1, 32),
        "The number of trackers of the same type to execute in parallel, each in its own browser", Scope.EXECUTION),

    /**
     * The number of screenshot attempts per tracker.
     */
    NUMBER_OF_SCREENSHOT_ATTEMPTS(Group.RUN_OPTIONS, "Attempts per tracker", "1", Range.of(1, 5),
        "The number of times to attempt to screenshot a tracker before marking it as a fail", Scope.EXECUTION),

    /**
     * The date format used to name the output directory.
     */
    OUTPUT_DIRECTORY_NAME_FORMAT(Group.OUTPUT, "Folder name format", "yyyy-MM-dd", OptionType.STRING, List.of(),
        "The date format used to name the output directory created for the screenshots", Scope.EXECUTION),

    /**
     * The parent path of the output directory.
     */
    OUTPUT_DIRECTORY_PARENT_PATH(Group.OUTPUT, "Screenshot folder", "/app/screenshots", OptionType.STRING, List.of(),
        "The location in which the dated output directory for the screenshots is created", Scope.EXECUTION),

    /**
     * The redaction types to apply.
     */
    REDACTION_TYPE(Group.RUN_OPTIONS, "Redaction", "BOX", OptionType.MULTI_SELECT, names(RedactionType.values()),
        "The redaction types to apply (if more than one is selected then multiple screenshots will be taken)", Scope.EXECUTION),

    /**
     * The secret used to encrypt the passwords stored in the database.
     */
    SECRET_KEY(Group.SYSTEM, "Secret key", "", OptionType.STRING, List.of(),
        "The secret used to encrypt the tracker passwords stored in the database, at least 16 characters (never shown)", Scope.STARTUP),

    /**
     * The local timezone.
     */
    TIMEZONE(Group.SYSTEM, "Timezone", "UTC", OptionType.STRING, List.of(),
        "The local timezone, used for the date in the names of the output directory and of downloaded screenshots", Scope.EXECUTION),

    /**
     * The web UI port.
     */
    WEB_UI_PORT(Group.SYSTEM, "Port", "8080", Range.of(1, 65_535),
        "The port on which the web UI is served", Scope.STARTUP);

    /**
     * All available {@link ConfigurationOption}s.
     */
    public static final Collection<ConfigurationOption> ALL_VALUES = List.of(values());

    private final Group group;
    private final String label;
    private final String defaultValue;
    private final OptionType type;
    private final List<String> allowedValues;
    private final String description;
    private final Scope scope;
    private final @Nullable Range range;

    ConfigurationOption(final Group group, final String label, final String defaultValue, final OptionType type,
        final List<String> allowedValues, final String description, final Scope scope) {
        this(group, label, defaultValue, type, allowedValues, description, scope, null);
    }

    // An OptionType.INTEGER option, with the range of valid values
    ConfigurationOption(final Group group, final String label, final String defaultValue, final Range range,
        final String description, final Scope scope) {
        this(group, label, defaultValue, OptionType.INTEGER, List.of(), description, scope, range);
    }

    ConfigurationOption(final Group group, final String label, final String defaultValue, final OptionType type,
        final List<String> allowedValues, final String description, final Scope scope, final @Nullable Range range) {
        this.group = group;
        this.label = label;
        this.defaultValue = defaultValue;
        this.type = type;
        this.allowedValues = allowedValues;
        this.description = description;
        this.scope = scope;
        this.range = range;
    }

    /**
     * Retrieve a {@link ConfigurationOption} by its environment variable name. The search is case-sensitive.
     *
     * @param environmentVariableName the environment variable name
     * @return the matching {@link ConfigurationOption}, or {@code null} if none is found
     */
    @Nullable
    public static ConfigurationOption find(final String environmentVariableName) {
        return ALL_VALUES
            .stream()
            .filter(option -> option.name().equals(environmentVariableName))
            .findAny()
            .orElse(null);
    }

    /**
     * The value of this option as set in the environment, if any.
     *
     * @return the {@link Optional} environment variable value
     */
    public Optional<String> environmentValue() {
        return Optional.ofNullable(System.getenv(name()));
    }

    /**
     * The value of the option when it is not set in the environment or overridden.
     *
     * @return the default value
     */
    public String defaultValue() {
        return defaultValue;
    }

    /**
     * The {@link Group} the option is displayed under on the Settings tab of the web UI.
     *
     * @return the {@link Group}
     */
    public Group group() {
        return group;
    }

    /**
     * The user-facing name of the option, displayed in the web UI.
     *
     * @return the label
     */
    public String label() {
        return label;
    }

    /**
     * The {@link OptionType} of the value.
     *
     * @return the {@link OptionType}
     */
    public OptionType type() {
        return type;
    }

    /**
     * The values allowed for {@link OptionType#SELECT} and {@link OptionType#MULTI_SELECT} options, otherwise empty.
     *
     * @return the allowed values
     */
    public List<String> allowedValues() {
        return allowedValues;
    }

    /**
     * A user-facing description of the option, including the {@link #range()} of an {@link OptionType#INTEGER} option with a maximum.
     *
     * @return the description
     */
    public String description() {
        if (range == null || range.maximum() == Integer.MAX_VALUE) {
            return description;
        }
        return "%s [min: %d, max: %d]".formatted(description, range.minimum(), range.maximum());
    }

    /**
     * The range of valid values of an {@link OptionType#INTEGER} option.
     *
     * @return the {@link Range}, or {@link Optional#empty()} if the option is not an {@link OptionType#INTEGER}
     */
    public Optional<Range> range() {
        return Optional.ofNullable(range);
    }

    /**
     * Parses the value of an {@link OptionType#INTEGER} option, which must be within its {@link #range()}.
     *
     * @param input the value to parse
     * @return the parsed value
     * @throws IllegalArgumentException if the value is not a number, or is outside the {@link #range()}
     * @throws IllegalStateException    if the option is not an {@link OptionType#INTEGER}
     */
    public int parseInteger(final String input) {
        if (range == null) {
            throw new IllegalStateException("[%s] Is not an integer option".formatted(name()));
        }

        final String trimmed = input.trim();
        final int integer;
        try {
            integer = Integer.parseInt(trimmed);
        } catch (final NumberFormatException e) {
            throw new IllegalArgumentException("[%s] Invalid input '%s', must be a valid number".formatted(name(), trimmed), e);
        }

        if (integer < range.minimum() || integer > range.maximum()) {
            throw new IllegalArgumentException(
                "[%s] Invalid input '%s', must be between %d and %d".formatted(name(), trimmed, range.minimum(), range.maximum()));
        }
        return integer;
    }

    /**
     * Finds the {@link #allowedValues()} entry matching the input, ignoring case.
     *
     * @param input the value to find
     * @return the matching allowed value, in its defined case
     * @throws IllegalArgumentException if the value is not one of the {@link #allowedValues()}
     */
    public String parseAllowedValue(final String input) {
        final String trimmed = input.trim();
        return allowedValues
            .stream()
            .filter(allowedValue -> allowedValue.equalsIgnoreCase(trimmed))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException(
                "[%s] Invalid value: '%s', must be one of: %s".formatted(name(), trimmed, allowedValues)));
    }

    /**
     * The {@link Scope} of the option, defining when a changed value takes effect.
     *
     * @return the {@link Scope}
     */
    public Scope scope() {
        return scope;
    }

    /**
     * Whether the option can be overridden from the web UI. Options with {@link Scope#STARTUP} (such as the web UI port) are only read when the
     * application starts, so can only be set through environment variables.
     *
     * @return {@code true} if the option can be changed at runtime
     */
    public boolean editableAtRuntime() {
        return scope != Scope.STARTUP;
    }

    /**
     * Whether the value is secret, so is never returned to the web UI or logged.
     *
     * @return {@code true} if the value is secret
     */
    public boolean sensitive() {
        return this == SECRET_KEY;
    }

    private static List<String> names(final Enum<?>[] values) {
        return Arrays.stream(values).map(Enum::name).toList();
    }

    /**
     * The range of valid values of an {@link OptionType#INTEGER} {@link ConfigurationOption}, both inclusive.
     *
     * @param minimum the smallest valid value
     * @param maximum the largest valid value ({@link Integer#MAX_VALUE} if unbounded)
     */
    public record Range(int minimum, int maximum) implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /**
         * Creates a {@link Range}.
         *
         * @param minimum the smallest valid value
         * @param maximum the largest valid value ({@link Integer#MAX_VALUE} if unbounded)
         * @return the {@link Range}
         * @throws IllegalArgumentException if {@code minimum} is greater than {@code maximum}
         */
        public static Range of(final int minimum, final int maximum) {
            if (minimum > maximum) {
                throw new IllegalArgumentException("Minimum %d is greater than maximum %d".formatted(minimum, maximum));
            }
            return new Range(minimum, maximum);
        }
    }

    /**
     * When a changed value of a {@link ConfigurationOption} takes effect.
     */
    public enum Scope {

        /**
         * Only read when the application starts, so can only be set as an environment variable.
         */
        STARTUP,

        /**
         * Read when an execution starts, and cannot be changed while an execution is in progress.
         */
        EXECUTION
    }

    /**
     * The group under which a {@link ConfigurationOption} is displayed on the Settings tab of the web UI, in the order of the constants.
     */
    public enum Group {

        /**
         * Options likely to be changed for an execution.
         */
        RUN_OPTIONS("Run options"),

        /**
         * Where and how the screenshots are saved.
         */
        OUTPUT("Output"),

        /**
         * The application itself.
         */
        SYSTEM("System");

        private final String label;

        Group(final String label) {
            this.label = label;
        }

        /**
         * The user-facing name of the group, displayed in the web UI.
         *
         * @return the label
         */
        public String label() {
            return label;
        }
    }

    /**
     * The type of value held by a {@link ConfigurationOption}, used to render an appropriate input in the web UI.
     */
    public enum OptionType {

        /**
         * A {@code true}/{@code false} value.
         */
        BOOLEAN,

        /**
         * A whole number.
         */
        INTEGER,

        /**
         * An ordered, comma-separated selection of {@link ConfigurationOption#allowedValues()}.
         */
        MULTI_SELECT,

        /**
         * A single selection of {@link ConfigurationOption#allowedValues()}.
         */
        SELECT,

        /**
         * Free text.
         */
        STRING
    }
}
