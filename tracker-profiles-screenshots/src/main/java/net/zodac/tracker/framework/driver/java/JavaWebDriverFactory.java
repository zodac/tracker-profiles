/*
 * BSD Zero Clause License
 *
 * Copyright (c) 2021-2025 zodac.net
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

package net.zodac.tracker.framework.driver.java;

import java.io.File;
import java.util.Map;
import net.zodac.tracker.framework.ApplicationConfiguration;
import net.zodac.tracker.framework.Configuration;
import net.zodac.tracker.framework.TrackerType;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.RemoteWebDriver;

/**
 * Factory class used to create an instance of a {@link RemoteWebDriver}.
 */
public final class JavaWebDriverFactory {

    private static final ApplicationConfiguration CONFIG = Configuration.get();

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
     *     <li>{@link TrackerType} is not {@link TrackerType#NON_ENGLISH} <b>or</b>
     *     {@link ApplicationConfiguration#enableTranslationToEnglish()} is {@code false}</li>
     * </ul>
     *
     * <p>
     * Otherwise it will run in full UI mode.
     *
     * @param trackerType whether {@link TrackerType} defining the execution method for this tracker.
     * @return the {@link RemoteWebDriver} instance
     */
    public static RemoteWebDriver createDriver(final TrackerType trackerType) {
        final ChromeOptions chromeOptions = new ChromeOptions();

        // User-defined options
        chromeOptions.addArguments("--window-size=" + CONFIG.browserDimensions());
        if (canTrackerUseHeadlessBrowser(trackerType)) {
            chromeOptions.addArguments("--headless=new");
            chromeOptions.addArguments("--start-maximized");
        }

        // Cache to avoid reloading data on subsequent runs
        chromeOptions.addArguments("--disk-cache-dir=" + CONFIG.browserDataStoragePath() + File.separator + "selenium");

        // Following 3 options are to ensure there are no conflicting issues running the browser on Linux
        chromeOptions.addArguments("--user-data-dir=" + CONFIG.browserDataStoragePath() + File.separator + System.nanoTime());
        chromeOptions.addArguments("--no-sandbox");
        chromeOptions.addArguments("--disable-dev-shm-usage");

        final Map<String, Object> driverPreferences = Map.of(
            // Disable password manager pop-ups
            "credentials_enable_service", false,
            "profile.password_manager_enabled", false
        );
        chromeOptions.setExperimentalOption("prefs", driverPreferences);

        // Additional flags to remove unnecessary information on browser
        chromeOptions.addArguments("--disable-blink-features=AutomationControlled");
        chromeOptions.addArguments("--disable-gpu");
        chromeOptions.addArguments("--disable-notifications");

        return new ChromeDriver(chromeOptions);
    }

    private static boolean canTrackerUseHeadlessBrowser(final TrackerType trackerType) {
        if (CONFIG.forceUiBrowser() || trackerType == TrackerType.MANUAL) {
            return false;
        }

        return trackerType != TrackerType.NON_ENGLISH || !CONFIG.enableTranslationToEnglish();
    }
}
