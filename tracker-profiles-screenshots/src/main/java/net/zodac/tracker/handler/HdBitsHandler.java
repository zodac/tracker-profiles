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
import net.zodac.tracker.framework.TrackerHandler;
import net.zodac.tracker.framework.gui.DisplayUtils;
import net.zodac.tracker.util.ScriptExecutor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;

/**
 * Implementation of {@link AbstractTrackerHandler} for the {@code HDBits} tracker.
 */
@TrackerHandler(name = "HDBits", needsManualInput = true, url = "https://hdbits.org/")
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

    @Override
    protected By loginButtonSelector() {
        return By.xpath("//input[@type='submit' and @value='Log in!']");
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
        ScriptExecutor.highlightElement(driver, captchaElement);
        DisplayUtils.userInputConfirmation(trackerName, String.format("Select the '%s' image", captchaTextElement.getText()));
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
     * For {@link HdBitsHandler}, there is a table with our IP address and passkey. We find the {@literal <}{@code tr}{@literal >} {@link WebElement}s
     * which have a {@literal <}{@code th}{@literal >} {@link WebElement} with the text value <b>Address</b> or <b>Passkey</b>.
     * From this {@literal <}{@code tr}{@literal >}, we find the child {@literal <}{@code td}{@literal >}, which needs its content redacted. There is
     * also a table of IPs that have been used for login that needs to be traversed and redacted.
     *
     * <p>
     * We do <b>not</b> execute the super-method {@link AbstractTrackerHandler#redactElements()}, due to constant 'stale element reference' errors.
     *
     * @see ScriptExecutor#redactInnerTextOf(JavascriptExecutor, WebElement)
     */
    @Override
    public int redactElements() {
        final WebElement addressValueElement = driver.findElement(By.xpath("//tr[td[text()='Address']]/td[2]"));
        ScriptExecutor.redactInnerTextOf(driver, addressValueElement);

        final WebElement passkeyValueElement = driver.findElement(By.xpath("//tr[td[text()='Passkey']]/td[2]"));
        ScriptExecutor.redactInnerTextOf(driver, passkeyValueElement);
        final int superRedactedElements = super.redactElements();

        final List<WebElement> securityLogPasswordElements = driver
            .findElements(By.xpath("//td[text()='Sec log']/following-sibling::td/table//td"))
            .stream()
            .filter(element -> doesElementContainEmailAddress(element) || doesElementContainIpAddress(element))
            .toList();

        for (final WebElement element : securityLogPasswordElements) {
            ScriptExecutor.redactInnerTextOf(driver, element);
        }

        return securityLogPasswordElements.size() + superRedactedElements;
    }

    @Override
    public Collection<By> getElementsPotentiallyContainingSensitiveInformation() {
        // Adding tag with definitely no data to redact, but needed for logging since we override the method otherwise
        return List.of(
            By.xpath("//div[@class='footer']")
        );
    }

    @Override
    protected By logoutButtonSelector() {
        return By.xpath("//a[contains(text(), 'Logout')]");
    }
}
