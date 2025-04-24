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
import java.util.List;
import net.zodac.tracker.framework.TrackerType;
import net.zodac.tracker.framework.annotation.TrackerHandler;
import net.zodac.tracker.framework.gui.DisplayUtils;
import net.zodac.tracker.util.PatternMatcher;
import net.zodac.tracker.util.ScriptExecutor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;

/**
 * Implementation of {@link AbstractTrackerHandler} for the {@code HDBits} tracker.
 */
@TrackerHandler(name = "HDBits", type = TrackerType.MANUAL, url = "https://hdbits.org/")
public class HdBitsHandler extends AbstractTrackerHandler {

    private static final Logger LOGGER = LogManager.getLogger();

    /**
     * Default constructor.
     *
     * @param driver      a {@link ChromeDriver} used to load web pages and perform UI actions
     * @param trackerUrls the URLs to the tracker
     */
    public HdBitsHandler(final ChromeDriver driver, final Collection<String> trackerUrls) {
        super(driver, trackerUrls);
    }

    @Override
    protected By usernameFieldSelector() {
        return By.xpath("//input[@name='uname' and @type='text']");
    }

    @Override
    protected By passwordFieldSelector() {
        return By.xpath("//input[@name='password' and @type='password']");
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * For {@link HdBitsHandler}, prior to clicking the login button with a successful username/password there is another field where an image needs
     * to be selected based on a text hint. This must be done within {@link DisplayUtils#INPUT_WAIT_DURATION}.
     *
     * <p>
     * Manual user interaction:
     * <ol>
     *     <li>Select the correct image</li>
     * </ol>
     */
    @Override
    protected void manualCheckBeforeLoginClick(final String trackerName) {
        final WebElement captchaTextElement = driver.findElement(By.xpath("//div[@class='captchaIntro']/p[1]/strong[1]"));
        LOGGER.info("\t\t >>> Waiting for user to select the '{}' image, for {} seconds", captchaTextElement.getText(),
            DisplayUtils.INPUT_WAIT_DURATION.getSeconds());

        final WebElement captchaElement = driver.findElement(By.id("captcha"));
        scriptExecutor.highlightElement(captchaElement);
        DisplayUtils.userInputConfirmation(trackerName, String.format("Select the '%s' image", captchaTextElement.getText()));
    }

    @Override
    protected By loginButtonSelector() {
        return By.xpath("//input[@type='submit' and @value='Log in!']");
    }

    @Override
    protected By postLoginSelector() {
        return By.id("news");
    }

    @Override
    protected By profilePageSelector() {
        return By.xpath("//div[@class='curuser-stats']//b//a[1]");
    }

    @Override
    protected void additionalActionOnProfilePage() {
        ScriptExecutor.explicitWait(Duration.ofSeconds(1L));
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * For {@link HdBitsHandler}, there is also a table entry with our passkey. We find the {@literal <}{@code tr}{@literal >} {@link WebElement}s
     * which has a {@literal <}{@code th}{@literal >} {@link WebElement} with the text value <b>Passkey</b>. From this
     * {@literal <}{@code tr}{@literal >}, we find the child {@literal <}{@code td}{@literal >}, which needs its content redacted.
     *
     * @see AbstractTrackerHandler#redactElements()
     * @see ScriptExecutor#redactInnerTextOf(WebElement, String)
     */
    @Override
    public int redactElements() {
        final WebElement passkeyValueElement = driver.findElement(By.xpath("//tr[td[text()='Passkey']]/td[2]"));
        scriptExecutor.redactInnerTextOf(passkeyValueElement, PatternMatcher.DEFAULT_REDACTION_TEXT);

        return 1 + super.redactElements();
    }

    @Override
    public Collection<By> getElementsPotentiallyContainingSensitiveInformation() {
        return List.of(
            By.xpath("//tr[td[text()='Address']]/td[2]"), // IP address
            By.xpath("//td[text()='Sec log']/following-sibling::td/table//td") // IP address history
        );
    }

    @Override
    protected By logoutButtonSelector() {
        return By.xpath("//a[contains(text(), 'Logout')]");
    }
}
