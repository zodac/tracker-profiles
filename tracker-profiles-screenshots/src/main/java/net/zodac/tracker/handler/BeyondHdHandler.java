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
import net.zodac.tracker.util.ScriptExecutor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;

/**
 * Implementation of {@link AbstractTrackerHandler} for the {@code BeyondHD} tracker.
 */
@TrackerHandler(name = "BeyondHD", needsManualInput = true, url = "https://beyond-hd.me/")
public class BeyondHdHandler extends AbstractTrackerHandler {

    private static final Logger LOGGER = LogManager.getLogger();

    /**
     * Default constructor.
     *
     * @param driver      a {@link ChromeDriver} used to load web pages and perform UI actions
     * @param trackerUrls the URLs to the tracker
     */
    public BeyondHdHandler(final ChromeDriver driver, final Collection<String> trackerUrls) {
        super(driver, trackerUrls);
    }

    @Override
    public By loginButtonSelector() {
        return By.id("login-button");
    }

    @Override
    protected By postLoginSelector() {
        return By.id("main");
    }

    @Override
    protected By profilePageSelector() {
        return By.xpath("//a[img[contains(@class, 'beta-image-avatar')]]");
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * For {@link BeyondHdHandler}, prior to clicking the login button with a successful username/password there is another field where a
     * Captcha needs to be entered. This must be done within {@link #DEFAULT_WAIT_FOR_MANUAL_INTERACTION}.
     *
     * <p>
     * Manual user interaction:
     * <ol>
     *     <li>Enter correct captcha value</li>
     * </ol>
     */
    @Override
    protected void manualCheckBeforeLoginClick() {
        LOGGER.info("\t\t >>> Waiting for user to enter captcha, for {} seconds", DEFAULT_WAIT_FOR_MANUAL_INTERACTION.getSeconds());

        final WebElement captchaElement = driver.findElement(By.xpath("//*[@id='captcha']/ancestor::div[1]"));
        ScriptExecutor.highlightElement(driver, captchaElement);
        ScriptExecutor.explicitWait(DEFAULT_WAIT_FOR_MANUAL_INTERACTION);
    }

    @Override
    public boolean canDisableFixedHeader() {
        final WebElement stickyElement = driver.findElement(By.id("stickyBar"));
        ScriptExecutor.updateCss(driver, stickyElement, "position", "static");
        return true;
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
