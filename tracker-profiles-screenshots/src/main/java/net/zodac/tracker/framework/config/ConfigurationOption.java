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

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import net.zodac.tracker.framework.TrackerType;
import net.zodac.tracker.framework.redactor.RedactionType;
import org.jspecify.annotations.Nullable;

/**
 * Definition of every environment variable that configures the application, used as the single source of truth for each variable's name, default
 * value and description. Used both by {@link ApplicationConfiguration} when loading values, and by the web UI to render the settings page.
 */
public enum ConfigurationOption { // NOPMD: DataClass - definitions of each environment variable, with accessors for the web UI

    /**
     * The height (in pixels) of the web browser.
     */
    BROWSER_HEIGHT("Browser", "1050", OptionType.INTEGER, List.of(),
        "The height (in pixels) of the web browser used to take screenshots (must be a supported resolution with BROWSER_WIDTH)"),

    /**
     * The width (in pixels) of the web browser.
     */
    BROWSER_WIDTH("Browser", "1680", OptionType.INTEGER, List.of(),
        "The width (in pixels) of the web browser used to take screenshots (must be a supported resolution with BROWSER_HEIGHT)"),

    /**
     * The comment character for the tracker CSV file.
     */
    CSV_COMMENT_SYMBOL("Tracker Input", "#", OptionType.CHARACTER, List.of(),
        "If this character is the first in a CSV row, the CSV row is considered a comment and not processed", false),

    /**
     * The path to the SQLite database used by the web UI.
     */
    DATABASE_PATH("Web UI", "/app/data/tracker-profiles.db", OptionType.STRING, List.of(),
        "The path to the SQLite database file storing tracker credentials and settings changed in the web UI (in its own docker volume)", false),

    /**
     * Whether to screenshot adult trackers.
     */
    ENABLE_ADULT_TRACKERS("Execution", "true", OptionType.BOOLEAN, List.of(),
        "Whether to take screenshots of trackers that primarily host adult content"),

    /**
     * Whether to translate non-English trackers.
     */
    ENABLE_TRANSLATION_TO_ENGLISH("Browser", "true", OptionType.BOOLEAN, List.of(),
        "Whether to translate non-English trackers to English"),

    /**
     * Whether to fail on an unsupported tracker in the CSV file.
     */
    FAIL_ON_UNSUPPORTED_TRACKER("Tracker Input", "true", OptionType.BOOLEAN, List.of(),
        "Whether to fail if a tracker in the CSV file has no matching handler implementation", false),

    /**
     * Whether to force a UI browser for all trackers.
     */
    FORCE_UI_BROWSER("Browser", "false", OptionType.BOOLEAN, List.of(),
        "Forces a browser with UI for each tracker, even for headless trackers (this will disable parallel execution)"),

    /**
     * Whether to time out user inputs.
     */
    INPUT_TIMEOUT_ENABLED("Execution", "false", OptionType.BOOLEAN, List.of(),
        "Whether to add a timeout for when a user-input is required, otherwise waits"),

    /**
     * The user input timeout in seconds.
     */
    INPUT_TIMEOUT_SECONDS("Execution", "300", OptionType.INTEGER, List.of(),
        "If INPUT_TIMEOUT_ENABLED is enabled, how long to wait for a user-input in seconds"),

    /**
     * The application log level.
     */
    LOG_LEVEL("Logging", "INFO", OptionType.SELECT, List.of("ALL", "TRACE", "DEBUG", "INFO", "WARN", "ERROR", "FATAL", "OFF"),
        "The logging level for application output"),

    /**
     * Whether to prefix log messages with the tracker name.
     */
    LOG_TRACKER_NAME("Logging", "true", OptionType.BOOLEAN, List.of(),
        "Whether to prefix each log message with the name of the tracker being screenshot"),

    /**
     * The number of parallel threads for headless trackers.
     */
    NUMBER_OF_PARALLEL_THREADS("Execution", "5", OptionType.INTEGER, List.of(),
        "The number of parallel browser threads to use for Headless trackers [min: 1, max: 32]"),

    /**
     * The number of screenshot attempts per tracker.
     */
    NUMBER_OF_SCREENSHOT_ATTEMPTS("Execution", "1", OptionType.INTEGER, List.of(),
        "The number of times to attempt to screenshot a tracker before marking it as a fail [min: 1, max: 5]"),

    /**
     * The date format used to name the output directory.
     */
    OUTPUT_DIRECTORY_NAME_FORMAT("Output", "yyyy-MM-dd", OptionType.STRING, List.of(),
        "The date format used to name the output directory created for the screenshots"),

    /**
     * The parent path of the output directory.
     */
    OUTPUT_DIRECTORY_PARENT_PATH("Output", "/app/screenshots", OptionType.STRING, List.of(),
        "The location in which the dated output directory for the screenshots is created"),

    /**
     * The progress bar complete character.
     */
    PROGRESS_BAR_COMPLETE_CHARACTER("Progress Bar", "█", OptionType.CHARACTER, List.of(),
        "The character used to render the completed portion of the console progress bar (must differ from the incomplete character)"),

    /**
     * Whether the progress bar is enabled.
     */
    PROGRESS_BAR_ENABLED("Progress Bar", "true", OptionType.BOOLEAN, List.of(),
        "Whether to render a progress bar at the bottom of the console output"),

    /**
     * The progress bar format.
     */
    PROGRESS_BAR_FORMAT("Progress Bar", ":bar :percent% | [:elapsed] | :units/:total-units trackers", OptionType.STRING, List.of(),
        "The format string for the console progress bar (must not be blank)"),

    /**
     * The progress bar incomplete character.
     */
    PROGRESS_BAR_INCOMPLETE_CHARACTER("Progress Bar", "░", OptionType.CHARACTER, List.of(),
        "The character used to render the incomplete portion of the console progress bar (must differ from the complete character)"),

    /**
     * The progress bar length.
     */
    PROGRESS_BAR_LENGTH("Progress Bar", "35", OptionType.INTEGER, List.of(),
        "The length (in characters) of the console progress bar [min: 10, max: 80]"),

    /**
     * The redaction types to apply.
     */
    REDACTION_TYPE("Output", "BOX", OptionType.MULTI_SELECT, names(RedactionType.values()),
        "The redaction types to apply (if more than one is selected then multiple screenshots will be taken)"),

    /**
     * The action when a screenshot already exists.
     */
    SCREENSHOT_EXISTS_ACTION("Output", ExistingScreenshotAction.CREATE_ANOTHER.name(), OptionType.SELECT, names(ExistingScreenshotAction.values()),
        "What to do when a screenshot for the tracker for the given date already exists"),

    /**
     * Whether to take a screenshot on error.
     */
    TAKE_SCREENSHOT_ON_ERROR("Output", "false", OptionType.BOOLEAN, List.of(),
        "Whether to take a screenshot of the current tracker page if any failure occurs (in a subdirectory called 'errors')"),

    /**
     * The local timezone.
     */
    TIMEZONE("Output", "UTC", OptionType.STRING, List.of(),
        "The local timezone, used to retrieve the current date to name the output directory"),

    /**
     * The execution order of tracker types.
     */
    TRACKER_EXECUTION_ORDER("Execution", "HEADLESS,MANUAL", OptionType.MULTI_SELECT, names(TrackerType.values()),
        "The order in which different tracker types should be executed, at least one must be selected"),

    /**
     * The path to the tracker CSV file.
     */
    TRACKER_INPUT_FILE_PATH("Tracker Input", "/app/screenshots/trackers.csv", OptionType.STRING, List.of(),
        "The path to the input tracker CSV file (used by the CLI, and imported into the database on first start of the web UI)", false),

    /**
     * Whether to start the web UI.
     */
    WEB_UI_ENABLED("Web UI", "true", OptionType.BOOLEAN, List.of(),
        "Whether to start the web UI, rather than executing once from the CSV file and exiting", false),

    /**
     * The web UI port.
     */
    WEB_UI_PORT("Web UI", "8080", OptionType.INTEGER, List.of(),
        "The port on which the web UI is served [min: 1, max: 65535]", false);

    /**
     * All available {@link ConfigurationOption}s.
     */
    public static final Collection<ConfigurationOption> ALL_VALUES = List.of(values());

    private final String group;
    private final String defaultValue;
    private final OptionType type;
    private final List<String> allowedValues;
    private final String description;
    private final boolean editableAtRuntime;

    ConfigurationOption(final String group, final String defaultValue, final OptionType type, final List<String> allowedValues,
        final String description) {
        this(group, defaultValue, type, allowedValues, description, true);
    }

    ConfigurationOption(final String group, final String defaultValue, final OptionType type, final List<String> allowedValues,
        final String description, final boolean editableAtRuntime) {
        this.group = group;
        this.defaultValue = defaultValue;
        this.type = type;
        this.allowedValues = allowedValues;
        this.description = description;
        this.editableAtRuntime = editableAtRuntime;
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
     * The group the option is displayed under in the web UI.
     *
     * @return the group name
     */
    public String group() {
        return group;
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
     * A user-facing description of the option.
     *
     * @return the description
     */
    public String description() {
        return description;
    }

    /**
     * Whether the option can be overridden from the web UI. Options that are only read when the application starts (such as the web UI port, or
     * the CSV file options, which are only used by the CLI and the initial import into the web UI database) can only be set through environment
     * variables.
     *
     * @return {@code true} if the option can be changed at runtime
     */
    public boolean editableAtRuntime() {
        return editableAtRuntime;
    }

    private static List<String> names(final Enum<?>[] values) {
        return Arrays.stream(values).map(Enum::name).toList();
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
         * A single character.
         */
        CHARACTER,

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
