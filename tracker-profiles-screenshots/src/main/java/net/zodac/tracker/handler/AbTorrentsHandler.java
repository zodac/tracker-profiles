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
import org.jspecify.annotations.Nullable;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.remote.RemoteWebDriver;

/**
 * Implementation of {@link AbstractTrackerHandler} for the {@code ABTorrents} tracker.
 */
@TrackerHandler(name = "ABTorrents", type = TrackerType.MANUAL, url = "https://abtorrents.me/")
public class AbTorrentsHandler extends AbstractTrackerHandler {

    /**
     * Default constructor.
     *
     * @param driver      a {@link RemoteWebDriver} used to load web pages and perform UI actions
     * @param trackerUrls the URLs to the tracker
     */
    public AbTorrentsHandler(final RemoteWebDriver driver, final Collection<String> trackerUrls) {
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
     * For {@link AbTorrentsHandler}, there is no login button to click. The output of {@link #manualCheckBeforeLoginClick(String)} should log in.
     */
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
     *     <li>Click the 'X' button to log in</li>
     * </ol>
     */
    @Override
    protected void manualCheckBeforeLoginClick(final String trackerName) {
        final WebElement captchaTextElement = driver.findElement(By.xpath("//span[@class='captchaText']"));
        LOGGER.info("\t\t >>> Waiting for user to select the '{}' image and click the 'X' button to log in, for {} seconds",
            captchaTextElement.getText(), DisplayUtils.INPUT_WAIT_DURATION.getSeconds());

        scriptExecutor.highlightElement(driver.findElement(By.id("captcha")));
        scriptExecutor.highlightElement(driver.findElement(By.xpath("//table[count(.//tr) >= 6]/tbody/tr[6]/td[1]")));
        DisplayUtils.userInputConfirmation(trackerName,
            String.format("Select the '%s' image and click the 'X' button to log in", captchaTextElement.getText()));
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
    public Collection<By> getElementsPotentiallyContainingSensitiveInformation() {
        return List.of(
            By.xpath("//tr[td[1][contains(text(), 'Connectable')]]/td[2]//span") // Last connected IP address
        );
    }

    @Override
    protected By logoutButtonSelector() {
        ScriptExecutor.explicitWait(Duration.ofSeconds(1L)); // Wait for the logout button to become visible and clickable again after scrolling
        return By.id("logoff");
    }
}
