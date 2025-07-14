/*
 * BSD Zero Clause License
 *
 * Copyright (c) 2024-2025 zodac.net
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

package net.zodac.tracker.framework.driver.python;

import net.zodac.tracker.framework.ApplicationConfiguration;
import net.zodac.tracker.framework.Configuration;
import net.zodac.tracker.framework.exception.DriverAttachException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.remote.RemoteWebDriver;

/**
 * Factory class used to create an instance of a {@link RemoteWebDriver} that is attached to a Selenium web browser that was launched by a Python
 * Flask server.
 *
 * <p>
 * The {@link net.zodac.tracker.framework.TrackerType#CLOUDFLARE_CHECK} means a tracker has a Cloudflare verification check during the login flow, and
 * this cannot be bypassed by the standard Selenium {@link RemoteWebDriver}. Instead, we use a Python package,
 * <a href="https://github.com/ultrafunkamsterdam/undetected-chromedriver">undetected-chromedriver</a>, which can be used to avoid Cloudflare
 * detection. We use a Flask webserver to allow the main Java application to start a web browser session using this package, and the webserver will
 * return the URL of this session, and the session ID.
 *
 * <p>
 * This information can be used to create a {@link RemoteWebDriver} and attach to the running
 * instance, which will allow us to continue the process of screenshotting the tracker without getting caught by the Cloudflare check.
 */
public final class PythonWebDriverFactory {

    private static final ApplicationConfiguration CONFIG = Configuration.get();
    private static final Logger LOGGER = LogManager.getLogger();

    private static final String EXPECTED_INITIAL_URL = "chrome://new-tab-page/";

    private PythonWebDriverFactory() {

    }

    /**
     * Creates an instance of {@link RemoteWebDriver} to be used by the {@link net.zodac.tracker.handler.AbstractTrackerHandler}, for a Selenium
     * web browser session that was started by a Python process.
     *
     * <p>
     * Currently it only runs in headful mode.
     *
     * @return the {@link RemoteWebDriver} instance
     */
    public static RemoteWebDriver createDriver() {
        final SeleniumSession seleniumSession = PythonHttpServerHandler.openSession(CONFIG.browserDataStoragePath(), CONFIG.browserDimensions());

        final RemoteWebDriver driver = AttachedRemoteWebDriver.create(seleniumSession);
        LOGGER.debug("Successfully attached to existing session");

        final String currentUrl = driver.getCurrentUrl();
        if (!EXPECTED_INITIAL_URL.equalsIgnoreCase(currentUrl)) {
            throw new DriverAttachException(
                String.format("Expected initial URL for attached driver '%s', found: '%s'", EXPECTED_INITIAL_URL, currentUrl));
        }

        return driver;
    }
}
