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

package net.zodac.tracker.framework.driver;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import net.zodac.tracker.framework.TrackerType;
import net.zodac.tracker.framework.config.ApplicationConfiguration;
import net.zodac.tracker.framework.config.Configuration;
import net.zodac.tracker.framework.driver.extension.Extension;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jspecify.annotations.Nullable;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeDriverService;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.RemoteWebDriver;

/**
 * Factory class used to create an instance of a {@link RemoteWebDriver}.
 */
public final class JavaWebDriverFactory {

    private static final String HEADLESS_USER_AGENT_MARKER = "HeadlessChrome";
    private static final String HEADLESS_USER_AGENT_REPLACEMENT = "Chrome";
    private static final File CHROMEDRIVER_EXECUTABLE_FILEPATH = new File("/usr/bin/chromedriver");
    private static final ApplicationConfiguration CONFIG = Configuration.get();
    private static final Logger LOGGER = LogManager.getLogger();
    private static final Map<RemoteWebDriver, Path> USER_DATA_DIRS = new ConcurrentHashMap<>();

    private JavaWebDriverFactory() {

    }

    /**
     * Creates an instance of {@link RemoteWebDriver} to be used by the {@link net.zodac.tracker.handler.AbstractTrackerHandler}. This is a standard
     * Java Selenium {@link RemoteWebDriver}, with some arguments to configure it for simpler automation.
     *
     * <p>
     * This {@link RemoteWebDriver} may run in headless mode, if the following conditions are met:
     *
     * <ul>
     *     <li>{@link ApplicationConfiguration#forceUiBrowser()} is not {@code true}</li>
     *     <li>{@link TrackerType} is not {@link TrackerType#MANUAL}</li>
     * </ul>
     *
     * <p>
     * Otherwise it will run in full UI mode.
     *
     * @param trackerType whether {@link TrackerType} defining the execution method for this tracker
     * @param extensions  any {@link Extension}s to be installed
     * @return the {@link RemoteWebDriver} instance
     */
    public static RemoteWebDriver createDriver(final TrackerType trackerType, final List<Extension> extensions) {
        LOGGER.trace("Creating Java driver");
        final ChromeOptions chromeOptions = new ChromeOptions();
        final Path userDataDir = BrowserDirectories.SESSIONS.resolve(UUID.randomUUID().toString());

        // User-defined options
        chromeOptions.addArguments("--window-size=" + CONFIG.browserDimensions());
        if (usesHeadlessBrowser(trackerType)) {
            LOGGER.trace("Using headless browser");
            chromeOptions.addArguments("--headless=new");

            // A headless browser identifies itself as such in its user agent, which is all that is needed for a website to block it
            final String userAgent = HeadlessUserAgentHolder.USER_AGENT;
            if (userAgent != null) {
                chromeOptions.addArguments("--user-agent=" + userAgent);
            }
        }

        // Cache to avoid reloading data on subsequent runs
        chromeOptions.addArguments("--disk-cache-dir=" + BrowserDirectories.CACHE);

        // Following 3 options are to ensure there are no conflicting issues running the browser on Linux
        chromeOptions.addArguments("--user-data-dir=" + userDataDir);
        chromeOptions.addArguments("--no-sandbox");
        chromeOptions.addArguments("--disable-dev-shm-usage");

        // Disable warnings when visiting HTTP-only sites
        chromeOptions.addArguments("--unsafely-treat-insecure-origin-as-secure=*");

        final Map<String, Object> driverPreferences = new HashMap<>();
        // Disable password manager pop-ups
        driverPreferences.put("credentials_enable_service", false);
        driverPreferences.put("profile.password_manager_enabled", false);

        if (CONFIG.enableTranslationToEnglish()) {
            // Translation options
            chromeOptions.addArguments("--lang=en");
            driverPreferences.put("intl.accept_languages", "en,en_US");
            driverPreferences.put("translate_accepted_count", 1);
            driverPreferences.put("translate.enabled", true);
            driverPreferences.put("translate_whitelists", getTranslationWhitelist());
        }

        chromeOptions.setExperimentalOption("prefs", driverPreferences);
        chromeOptions.setExperimentalOption("excludeSwitches", List.of("enable-automation"));
        chromeOptions.setExperimentalOption("useAutomationExtension", false);

        // Additional flags to remove unnecessary information on browser
        chromeOptions.addArguments("--disable-blink-features=AutomationControlled");
        chromeOptions.addArguments("--disable-gpu");
        chromeOptions.addArguments("--disable-notifications");
        chromeOptions.addArguments("--ignore-certificate-errors");

        for (final Extension extension : extensions) {
            LOGGER.trace("Installing extension {} from '{}'", extension.getClass().getSimpleName(), extension.path());
            final File extensionFile = new File(extension.path());
            chromeOptions.addExtensions(extensionFile);
        }
        LOGGER.trace("Creating driver with following options: {}", chromeOptions);

        final ChromeDriver driver = startDriver(chromeOptions);
        applyConfiguredSize(driver);
        LOGGER.trace("Returning created driver");
        USER_DATA_DIRS.put(driver, userDataDir);
        return driver;
    }

    /**
     * Whether a tracker of the given {@link TrackerType} is executed in a headless browser, or in a browser with a UI. A headless browser is used
     * when both of the following conditions are met:
     *
     * <ul>
     *     <li>{@link ApplicationConfiguration#forceUiBrowser()} is not {@code true}</li>
     *     <li>{@link TrackerType} is not {@link TrackerType#MANUAL}</li>
     * </ul>
     *
     * @param trackerType the {@link TrackerType} of the tracker
     * @return {@code true} if the tracker is executed in a headless browser
     */
    public static boolean usesHeadlessBrowser(final TrackerType trackerType) {
        if (CONFIG.forceUiBrowser()) {
            LOGGER.trace("UI browser is forced");
            return false;
        }

        return trackerType != TrackerType.MANUAL;
    }

    private static ChromeDriver startDriver(final ChromeOptions chromeOptions) {
        if (CHROMEDRIVER_EXECUTABLE_FILEPATH.exists()) {
            // Not closing the service since it takes 5 seconds to close
            final ChromeDriverService service = new ChromeDriverService
                .Builder()
                .usingDriverExecutable(CHROMEDRIVER_EXECUTABLE_FILEPATH)
                .build();
            LOGGER.trace("Creating driver with chromedriver executable at '{}'", CHROMEDRIVER_EXECUTABLE_FILEPATH.getAbsolutePath());
            return new ChromeDriver(service, chromeOptions);
        }

        LOGGER.trace("Creating driver without chromedriver executable filepath");
        return new ChromeDriver(chromeOptions);
    }

    // Starts a short-lived headless browser to read its user agent, then removes the marker identifying it as headless. Reading the user agent
    // from the browser (rather than hard-coding it) keeps the rest of the user agent consistent with the installed browser version.
    private static @Nullable String findHeadlessUserAgent() {
        LOGGER.trace("Finding user agent to be used for headless browsers");
        final ChromeOptions chromeOptions = new ChromeOptions();
        chromeOptions.addArguments("--headless=new", "--no-sandbox", "--disable-dev-shm-usage", "--disable-gpu");

        ChromeDriver driver = null;
        try {
            driver = startDriver(chromeOptions);
            final Object userAgent = driver.executeCdpCommand("Browser.getVersion", Map.of()).get("userAgent");
            if (userAgent instanceof final String headlessUserAgent && !headlessUserAgent.isBlank()) {
                final String cleanedUserAgent = headlessUserAgent.replace(HEADLESS_USER_AGENT_MARKER, HEADLESS_USER_AGENT_REPLACEMENT);
                LOGGER.trace("User agent for headless browsers: '{}'", cleanedUserAgent);
                return cleanedUserAgent;
            }

            LOGGER.debug("Browser did not return a user agent, headless browsers will use their default user agent");
            return null;
        } catch (final WebDriverException e) {
            LOGGER.debug("Unable to find the browser's user agent, headless browsers will use their default user agent", e);
            return null;
        } finally {
            if (driver != null) {
                driver.quit();
            }
        }
    }

    // Initialised on first use, so that the additional browser is only started if a headless browser is actually needed
    private static final class HeadlessUserAgentHolder {

        private static final @Nullable String USER_AGENT = findHeadlessUserAgent();
    }

    /**
     * Sweeps and deletes any stale user-data directories under the namespaced sessions subdirectory of the configured browser data storage path.
     *
     * <p>
     * Previous JVM runs may leave UUID-named subdirectories behind if the process was interrupted before {@link #deleteUserDataDir(RemoteWebDriver)}
     * could clean them up. Any UUID-named subdirectory of the sessions path that does not belong to a currently tracked driver is deleted.
     * Scanning is restricted to the {@link BrowserDirectories#SESSIONS} subdirectory to avoid accidentally deleting unrelated UUID-named directories
     * that may exist elsewhere in the configured storage path.
     *
     * <p>
     * Safe to call multiple times, as directories belonging to active drivers are excluded from deletion.
     */
    static void sweepStaleUserDataDirs() {
        final Path sessionsDirectory = BrowserDirectories.SESSIONS;
        if (!Files.isDirectory(sessionsDirectory)) {
            return;
        }

        final Set<Path> activeDirs = new HashSet<>(USER_DATA_DIRS.values());
        try (final Stream<Path> children = Files.list(sessionsDirectory)) {
            final List<Path> staleDirs = children
                .filter(JavaWebDriverFactory::isUuidDirectory)
                .filter(p -> !activeDirs.contains(p))
                .toList();

            if (staleDirs.isEmpty()) {
                LOGGER.trace("No stale user-data directories to clean");
            } else {
                LOGGER.debug("Sweeping {} stale user-data directories", staleDirs.size());
                staleDirs.forEach(JavaWebDriverFactory::deleteDirectory);
            }
        } catch (final IOException e) {
            LOGGER.debug("Failed to sweep stale user-data directories in '{}'", sessionsDirectory, e);
            LOGGER.warn("Failed to sweep stale user-data directories in '{}': {}", sessionsDirectory, e.getMessage());
        }
    }

    /**
     * Removes the user-data directory associated with the given {@link RemoteWebDriver} and deletes it from disk.
     *
     * <p>
     * The directory is tracked from the moment the driver was created by {@link #createDriver(TrackerType, List)}. If no
     * directory is registered for the driver, this method is a no-op.
     *
     * @param driver the {@link RemoteWebDriver} whose user-data directory should be deleted
     */
    static void deleteUserDataDir(final RemoteWebDriver driver) {
        final Path dir = USER_DATA_DIRS.remove(driver);
        if (dir != null) {
            LOGGER.trace("Deleting user-data directory '{}'", dir);
            deleteDirectory(dir);
        }
    }

    private static boolean isUuidDirectory(final Path path) {
        final Path fileName = path.getFileName();
        if (!Files.isDirectory(path) || fileName == null) {
            return false;
        }

        try {
            UUID.fromString(fileName.toString());
            return true;
        } catch (final IllegalArgumentException _) {
            return false;
        }
    }

    private static void deleteDirectory(final Path dir) {
        try (final Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder())
                .forEach(JavaWebDriverFactory::deleteWithLogging);
        } catch (final IOException e) {
            LOGGER.trace("Could not delete user-data directory '{}'", dir, e);
        }
    }

    private static void deleteWithLogging(final Path filePath) {
        try {
            Files.delete(filePath);
        } catch (final IOException e) {
            LOGGER.trace("Could not delete '{}'", filePath, e);
        }
    }

    private static void applyConfiguredSize(final ChromeDriver driver) {
        LOGGER.trace("Applying display size for driver");
        final Dimension size = parseDimensions();
        driver.manage().window().setSize(size);

        final Map<String, Object> cdpOverrides = HashMap.newHashMap(4);
        cdpOverrides.put("width", size.getWidth());
        cdpOverrides.put("height", size.getHeight());
        cdpOverrides.put("deviceScaleFactor", 1);
        cdpOverrides.put("mobile", false);

        driver.executeCdpCommand("Emulation.setDeviceMetricsOverride", cdpOverrides);
    }

    // No need to perform any validation, browserDimensions has been parsed already
    private static Dimension parseDimensions() {
        final String[] parts = CONFIG.browserDimensions().split(",", -1);
        final int width = Integer.parseInt(parts[0].trim());
        final int height = Integer.parseInt(parts[1].trim());
        return new Dimension(width, height);
    }

    private static Map<String, String> getTranslationWhitelist() {
        final String[] baseLanguages = {
            "af", "am", "ar", "az", "be", "bg", "bn", "bs", "ca", "co",
            "cs", "cy", "da", "de", "el", "eo", "es", "et", "eu", "fa",
            "fi", "fr", "fy", "ga", "gd", "gl", "gu", "ha", "he", "hi",
            "hr", "hu", "hy", "id", "ig", "is", "it", "iw", "ja", "jw",
            "ka", "kk", "km", "kn", "ko", "ku", "ky", "la", "lb", "lo",
            "lt", "lv", "mg", "mi", "mk", "ml", "mn", "mr", "ms", "mt",
            "my", "ne", "nl", "no", "ny", "pa", "pl", "ps", "pt", "ro",
            "ru", "sd", "si", "sk", "sl", "sm", "sn", "so", "sq", "sr",
            "st", "su", "sv", "sw", "ta", "te", "tg", "th", "tr", "uk",
            "ur", "uz", "vi", "xh", "yi", "yo", "zh", "zu"
        };

        final Map<String, String> translateWhitelists = HashMap.newHashMap(baseLanguages.length);
        for (final String baseLanguage : baseLanguages) {
            translateWhitelists.put(baseLanguage, "en");
        }
        return translateWhitelists;
    }
}
