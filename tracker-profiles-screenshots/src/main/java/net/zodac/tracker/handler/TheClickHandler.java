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

package net.zodac.tracker.handler;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import net.zodac.tracker.framework.TrackerType;
import net.zodac.tracker.framework.annotation.TrackerHandler;
import net.zodac.tracker.framework.gui.DisplayUtils;
import net.zodac.tracker.util.ScriptExecutor;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.remote.RemoteWebDriver;

/**
 * Common implementation of {@link AbstractTrackerHandler} for {@code .click} family of trackers.
 */
@TrackerHandler(name = "TheEmpire", type = TrackerType.MANUAL, url = "https://theempire.click/")
@TrackerHandler(name = "TheGeeks", type = TrackerType.MANUAL, url = "https://thegeeks.click/")
public class TheClickHandler extends AbstractTrackerHandler {

    /**
     * Default constructor.
     *
     * @param driver      a {@link RemoteWebDriver} used to load web pages and perform UI actions
     * @param trackerUrls the URLs to the tracker
     */
    public TheClickHandler(final RemoteWebDriver driver, final Collection<String> trackerUrls) {
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
     * For {@link TheClickHandler}, prior to clicking the login button with a captcha that needs to be clicked (and verified if necessary). This must
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
        LOGGER.info("\t\t >>> Waiting for user to solve the captcha, for {} seconds", DisplayUtils.INPUT_WAIT_DURATION.getSeconds());

        final WebElement captchaElement = driver.findElement(By.xpath("//div[@class='h-captcha']"));
        scriptExecutor.highlightElement(captchaElement);
        DisplayUtils.userInputConfirmation(trackerName, "Solve the captcha");
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
    public Collection<By> getElementsPotentiallyContainingSensitiveInformation() {
        return List.of(
            By.xpath("//tbody/tr[td[1][contains(text(), 'IP Address')]]/td[2]") // IP address
        );
    }

    @Override
    protected By logoutButtonSelector() {
        return By.xpath("//span[@class='statuslink']/b[1]/a[2]");
    }
}
