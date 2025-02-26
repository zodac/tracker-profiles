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
@TrackerHandler(name = "HDBits", url = "https://hdbits.org/")
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

    @Override
    protected By profilePageSelector() {
        return By.xpath("//div[@class='curuser-stats']//b//a[1]");
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * For {@link HdBitsHandler}, prior to clicking the login button with a successful username/password there is another field where an image needs
     * to be selected based on a text hint. This must be done within {@link #DEFAULT_WAIT_FOR_MANUAL_INTERACTION}.
     *
     * <p>
     * Manual user interaction:
     * <ol>
     *     <li>Select the correct image</li>
     * </ol>
     */
    @Override
    protected void manualCheckBeforeLoginClick() {
        LOGGER.info("\t>>> Waiting for user to select valid image, for {} seconds", DEFAULT_WAIT_FOR_MANUAL_INTERACTION.getSeconds());
        ScriptExecutor.explicitWait(DEFAULT_WAIT_FOR_MANUAL_INTERACTION);
    }

    @Override
    protected void additionalWaitOnProfilePage() {
        ScriptExecutor.explicitWait(Duration.ofSeconds(1L));
    }

    /**
     * For {@link HdBitsHandler}, there is a table with our IP address and passkey. We find the {@literal <}{@code tr}{@literal >} {@link WebElement}s
     * which have a {@literal <}{@code th}{@literal >} {@link WebElement} with the text value <b>Address</b> or <b>Passkey</b>.
     * From this {@literal <}{@code tr}{@literal >}, we find the child {@literal <}{@code td}{@literal >}, which needs its content redacted.
     *
     * <p>
     * We do <b>not</b> execute the super-method {@link AbstractTrackerHandler#redactElements()}, only the explicit redactions defined here.
     *
     * @see ScriptExecutor#redactInnerTextOf(JavascriptExecutor, WebElement)
     */
    @Override
    public int redactElements() {
        final WebElement addressValueElement = driver.findElement(By.xpath("//tr[td[text()='Address']]/td[2]"));
        ScriptExecutor.redactInnerTextOf(driver, addressValueElement);

        final WebElement passkeyValueElement = driver.findElement(By.xpath("//tr[td[text()='Passkey']]/td[2]"));
        ScriptExecutor.redactInnerTextOf(driver, passkeyValueElement);

        return 2;
    }

    @Override
    protected By logoutButtonSelector() {
        return By.xpath("//a[contains(text(), 'Logout')]");
    }
}
