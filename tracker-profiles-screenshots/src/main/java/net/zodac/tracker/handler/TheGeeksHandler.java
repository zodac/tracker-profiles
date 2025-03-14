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

import java.time.Duration;
import java.util.Collection;
import net.zodac.tracker.framework.TrackerHandler;
import net.zodac.tracker.framework.gui.DisplayUtils;
import net.zodac.tracker.util.ScriptExecutor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;

/**
 * Implementation of {@link AbstractTrackerHandler} for the {@code TheGeeks} tracker.
 */
// TODO: Update annotation to easily combine similar trackers (like this and TheEmpire)
@TrackerHandler(name = "TheGeeks", needsManualInput = true, url = "https://thegeeks.click/")
public class TheGeeksHandler extends AbstractTrackerHandler {

    private static final Logger LOGGER = LogManager.getLogger();

    /**
     * Default constructor.
     *
     * @param driver      a {@link ChromeDriver} used to load web pages and perform UI actions
     * @param trackerUrls the URLs to the tracker
     */
    public TheGeeksHandler(final ChromeDriver driver, final Collection<String> trackerUrls) {
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

    /**
     * {@inheritDoc}
     *
     * <p>
     * For {@link TheGeeksHandler}, prior to clicking the login button with a captcha that needs to be clicked (and verified if necessary). This must
     * be done within {@link DisplayUtils#INPUT_WAIT_DURATION}.
     *
     * <p>
     * Manual user interaction:
     * <ol>
     *     <li>Click and pass the captcha</li>
     * </ol>
     */
    @Override
    protected void manualCheckBeforeLoginClick(final String trackerName) {
        LOGGER.info("\t\t >>> Waiting for user to click the captcha, for {} seconds", DisplayUtils.INPUT_WAIT_DURATION.getSeconds());

        final WebElement captchaElement = driver.findElement(By.xpath("//div[@class='h-captcha']"));
        ScriptExecutor.highlightElement(driver, captchaElement);
        DisplayUtils.userInputConfirmation(trackerName, "Click the captcha");
    }

    @Override
    protected By loginButtonSelector() {
        return By.xpath("//input[@type='submit' and @value='Log in!']");
    }

    @Override
    protected By postLoginSelector() {
        return By.xpath("//span[@class='statuslink']");
    }

    @Override
    protected By profilePageSelector() {
        return By.xpath("//span[@class='statuslink']/b[1]/a[1]");
    }

    @Override
    protected void additionalActionOnProfilePage() {
        ScriptExecutor.explicitWait(Duration.ofSeconds(1L));
    }

    @Override
    protected By logoutButtonSelector() {
        return By.xpath("//span[@class='statuslink']/b[1]/a[2]");
    }
}
