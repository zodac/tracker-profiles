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
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.List;
import net.zodac.tracker.framework.TrackerType;
import net.zodac.tracker.framework.annotation.TrackerHandler;
import net.zodac.tracker.framework.gui.DisplayUtils;
import net.zodac.tracker.util.ScriptExecutor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;

/**
 * Implementation of {@link AbstractTrackerHandler} for the {@code PassThePopcorn} tracker.
 */
@TrackerHandler(name = "PassThePopcorn", type = TrackerType.MANUAL, url = "https://passthepopcorn.me/")
public class PassThePopcornHandler extends AbstractTrackerHandler {

    private static final Logger LOGGER = LogManager.getLogger();

    /**
     * Default constructor.
     *
     * @param driver      a {@link ChromeDriver} used to load web pages and perform UI actions
     * @param trackerUrls the URLs to the tracker
     */
    public PassThePopcornHandler(final ChromeDriver driver, final Collection<String> trackerUrls) {
        super(driver, trackerUrls);
    }

    @Override
    protected By loginButtonSelector() {
        ScriptExecutor.explicitWait(Duration.of(1L, ChronoUnit.SECONDS)); // Wait for the hidden captcha to load
        return By.id("login-button");
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * For {@link PassThePopcornHandler}, after clicking the login button with a successful username/password, another section pops up. There is a
     * multiple-choice question, where the correct movie title must be chosen that matches the poster, and the login button pressed again.
     * This must be done within {@link DisplayUtils#INPUT_WAIT_DURATION}.
     *
     * <p>
     * Manual user interactions:
     * <ol>
     *     <li>Select correct answer to question</li>
     *     <li>Click the login button</li>
     * </ol>
     */
    @Override
    protected void manualCheckAfterLoginClick(final String trackerName) {
        final String initialUrl = driver.getCurrentUrl();
        LOGGER.info("\t\t >>> Waiting for user to select correct movie title and click the login button, for {} seconds",
            DisplayUtils.INPUT_WAIT_DURATION.getSeconds());

        final WebElement selectionElement = driver.findElement(By.xpath("//div[@id='captcha_container']"));
        ScriptExecutor.highlightElement(driver, selectionElement);
        DisplayUtils.userInputConfirmation(trackerName, "Select the correct movie and click the login button");

        // If the user didn't click 'login', do it for them
        final String nextUrl = driver.getCurrentUrl();
        if (nextUrl == null || nextUrl.equalsIgnoreCase(initialUrl)) {
            final By loginButtonSelector = loginButtonSelector();
            if (loginButtonSelector != null) {
                final WebElement loginButton = driver.findElement(loginButtonSelector);
                clickButton(loginButton);
            }
        }
    }

    @Override
    protected By postLoginSelector() {
        return By.id("userinfo");
    }

    @Override
    protected By profilePageSelector() {
        return By.xpath("//a[@class='user-info-bar__link']");
    }

    @Override
    public Collection<By> getElementsPotentiallyContainingSensitiveInformation() {
        return List.of(
            By.xpath("//ul[contains(@class, 'list')]/li[contains(text(), 'Email:')]/a[1]"), // Email
            By.xpath("//a[@title='Manage Sessions']") // Footer with last used IP address
        );
    }

    @Override
    protected By logoutButtonSelector() {
        return By.xpath("//li[@id='nav_logout']//a[text()='Logout']");
    }
}
