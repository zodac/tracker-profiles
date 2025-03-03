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

package net.zodac.tracker.handler;

import java.util.Collection;
import net.zodac.tracker.framework.TrackerHandler;
import net.zodac.tracker.gui.DisplayUtils;
import net.zodac.tracker.util.ScriptExecutor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jspecify.annotations.Nullable;
import org.openqa.selenium.By;
import org.openqa.selenium.chrome.ChromeDriver;

/**
 * Implementation of {@link AbstractTrackerHandler} for the {@code ABTorrents} tracker.
 */
@TrackerHandler(name = "ABTorrents", needsManualInput = true, url = "https://abtorrents.me/")
public class AbTorrentsHandler extends AbstractTrackerHandler {

    private static final Logger LOGGER = LogManager.getLogger();

    /**
     * Default constructor.
     *
     * @param driver      a {@link ChromeDriver} used to load web pages and perform UI actions
     * @param trackerUrls the URLs to the tracker
     */
    public AbTorrentsHandler(final ChromeDriver driver, final Collection<String> trackerUrls) {
        super(driver, trackerUrls);
    }

    @Override
    protected By usernameFieldSelector() {
        return By.xpath("//input[@name='username' and @type='text']");
    }

    @Override
    protected By passwordFieldSelector() {
        return By.xpath("//input[@name='password' and @type='password']");
    }

    @Nullable
    @Override
    protected By loginButtonSelector() {
        return null;
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * For {@link AbTorrentsHandler}, there is no login button to click. After the username/password is entered the user interaction must be
     * completed.
     * There is an icon to be selected, and then an 'X' button to be clicked to perform the login. This must be done within
     * {@link DisplayUtils#INPUT_WAIT_DURATION}.
     *
     * <p>
     * Manual user interactions:
     * <ol>
     *     <li>Select the correct image</li>
     *     <li>Click the 'X'' button to log in</li>
     * </ol>
     */
    @Override
    protected void manualCheckAfterLoginClick(final String trackerName) {
        LOGGER.info("\t\t >>> Waiting for user to select the correct image and click the 'X' button to log in, for {} seconds",
            DisplayUtils.INPUT_WAIT_DURATION.getSeconds());

        ScriptExecutor.highlightElement(driver, driver.findElement(By.id("captcha")));
        ScriptExecutor.highlightElement(driver, driver.findElement(By.xpath("//td[@id='control']//table[1]//tbody[1]//tr[6]")));
        DisplayUtils.userInputConfirmation(trackerName, "Select the correct image and click the 'X' button to log in");
    }

    @Override
    protected By postLoginSelector() {
        return By.id("base_usermenu");
    }

    @Override
    protected By profilePageSelector() {
        return By.xpath("//div[@id='base_usermenu']//div[1]//span[1]//a[1]");
    }

    @Override
    protected By logoutButtonSelector() {
        return By.id("logoff");
    }
}
