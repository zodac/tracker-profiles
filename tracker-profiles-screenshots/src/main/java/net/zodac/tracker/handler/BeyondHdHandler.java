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

import net.zodac.tracker.framework.TrackerHandlerType;
import net.zodac.tracker.util.ScriptExecutor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;

/**
 * Implementation of {@link AbstractTrackerHandler} for the {@code BeyondHD} tracker.
 */
@TrackerHandlerType(trackerName = "BeyondHD")
public class BeyondHdHandler extends AbstractTrackerHandler {

    private static final double ZOOM_LEVEL_FOR_SCREENSHOT = 0.75D;
    private static final Logger LOGGER = LogManager.getLogger();

    /**
     * Default constructor.
     *
     * @param driver a {@link ChromeDriver} used to load web pages and perform UI actions
     */
    public BeyondHdHandler(final ChromeDriver driver) {
        super(driver);
    }

    @Override
    public By loginButtonSelector() {
        return By.id("login-button");
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * For {@link BeyondHdHandler}, prior to clicking the login button with a successful username/password there is another section. Based on this
     * pop-up, a captcha needs to be entered. This must be done within {@link #DEFAULT_WAIT_FOR_MANUAL_INTERACTION}.
     *
     * <p>
     * Manual user interaction:
     * <ol>
     *     <li>Enter correct captcha value</li>
     * </ol>
     */
    @Override
    protected void manualCheckBeforeLoginClick() {
        LOGGER.info("\t>>> Waiting for user to enter captcha, for {} seconds", DEFAULT_WAIT_FOR_MANUAL_INTERACTION.getSeconds());
        ScriptExecutor.explicitWait(DEFAULT_WAIT_FOR_MANUAL_INTERACTION);
    }

    @Override
    public double zoomLevel() {
        return ZOOM_LEVEL_FOR_SCREENSHOT;
    }

    @Override
    protected By logoutButtonSelector() {
        // Highlight the nav bar to make the logout button interactable
        final By logoutParentBy = By.xpath("//div[contains(@class, 'bhd-td')]//div[contains(@class, 'dropmenu')]");
        final WebElement logoutParent = driver.findElement(logoutParentBy);
        ScriptExecutor.moveTo(driver, logoutParent);

        return By.xpath("//a[contains(text(), 'Logout')]");
    }
}
