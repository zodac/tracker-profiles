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

package net.zodac.tracker.framework;

import java.io.File;
import java.util.List;
import java.util.Map;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

/**
 * Factory class used to create an instance of a {@link org.openqa.selenium.WebDriver}.
 */
final class WebDriverFactory {

    private static final ApplicationConfiguration CONFIG = Configuration.get();

    private WebDriverFactory() {

    }

    /**
     * Create an instance of {@link ChromeDriver} to be used by the {@link net.zodac.tracker.handler.AbstractTrackerHandler}.
     *
     * @param trackerType whether {@link TrackerType} defining the execution method for this tracker.
     * @return the {@link ChromeDriver} instance
     */
    static ChromeDriver createChromeDriver(final TrackerType trackerType) {
        final ChromeOptions chromeOptions = new ChromeOptions();

        // User-defined options
        chromeOptions.addArguments("window-size=" + CONFIG.browserDimensions());
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
        chromeOptions.addArguments("--disable-gpu");
        chromeOptions.addArguments("--disable-notifications");
        chromeOptions.addArguments("disable-infobars");
        chromeOptions.setExperimentalOption("excludeSwitches", List.of("enable-automation"));

        return new ChromeDriver(chromeOptions);
    }

    private static boolean canTrackerUseHeadlessBrowser(final TrackerType trackerType) {
        return trackerType == TrackerType.HEADLESS && CONFIG.useHeadlessBrowser()
            || trackerType == TrackerType.NON_ENGLISH && !CONFIG.translateToEnglish()
            || trackerType == TrackerType.MANUAL_INPUT_NEEDED && !CONFIG.includeTrackersNeedingUi();
    }
}
