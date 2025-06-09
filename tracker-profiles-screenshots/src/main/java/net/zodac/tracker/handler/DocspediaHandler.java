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

import java.util.Collection;
import java.util.List;
import net.zodac.tracker.framework.TrackerType;
import net.zodac.tracker.framework.annotation.TrackerHandler;
import net.zodac.tracker.framework.gui.DisplayUtils;
import net.zodac.tracker.util.PatternMatcher;
import net.zodac.tracker.util.ScriptExecutor;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.remote.RemoteWebDriver;

/**
 * Implementation of {@link AbstractTrackerHandler} for the {@code Docspedia} tracker.
 */
@TrackerHandler(name = "Docspedia", type = TrackerType.MANUAL, url = "https://docspedia.world/")
public class DocspediaHandler extends AbstractTrackerHandler {

    /**
     * Default constructor.
     *
     * @param driver      a {@link RemoteWebDriver} used to load web pages and perform UI actions
     * @param trackerUrls the URLs to the tracker
     */
    public DocspediaHandler(final RemoteWebDriver driver, final Collection<String> trackerUrls) {
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
     * For {@link DocspediaHandler}, prior to clicking the login button with a successful username/password there is another field where a
     * Captcha needs to be entered. This must be done within {@link DisplayUtils#INPUT_WAIT_DURATION}.
     *
     * <p>
     * Manual user interaction:
     * <ol>
     *     <li>Enter correct captcha value</li>
     * </ol>
     */
    @Override
    protected void manualCheckBeforeLoginClick(final String trackerName) {
        LOGGER.info("\t\t >>> Waiting for user to enter captcha, for {} seconds", DisplayUtils.INPUT_WAIT_DURATION.getSeconds());

        final WebElement captchaElement = driver.findElement(By.id("captcha"));
        scriptExecutor.highlightElement(captchaElement);
        DisplayUtils.userInputConfirmation(trackerName, "Solve the captcha");
    }

    @Override
    protected By loginButtonSelector() {
        return By.xpath("//input[@value='Login' and @type='submit']");
    }

    @Override
    protected By postLoginSelector() {
        return By.id("navigation");
    }

    @Override
    protected By profilePageSelector() {
        return By.xpath("//div[@class='statusbar']/div[2]/div[1]/a[1]");
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * For {@link DocspediaHandler}, there is a table with our passkey. We
     * find the {@literal <}{@code tr}{@literal >} {@link WebElement} which has a {@literal <}{@code td}{@literal >} {@link WebElement} with the text
     * value <b>Torrent Passkey</b>. From this {@literal <}{@code tr}{@literal >}, we find the child {@literal <}{@code td}{@literal >}, which needs
     * its content redacted.
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
            By.xpath("//table/tbody/tr[td[contains(text(), 'Address')]]/td[2]") // IP address
        );
    }

    @Override
    protected By logoutButtonSelector() {
        return By.xpath("//a[contains(text(), 'Logout')]");
    }
}
