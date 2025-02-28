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
import java.util.List;
import net.zodac.tracker.framework.TrackerHandler;
import net.zodac.tracker.util.ScriptExecutor;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;

/**
 * Implementation of {@link AbstractTrackerHandler} for the {@code Cathode-Ray.Tube} tracker.
 */
@TrackerHandler(name = "Cathode-Ray.Tube", needsManualInput = false, url = "https://www.cathode-ray.tube/")
public class CathodeRayTubeHandler extends AbstractTrackerHandler {

    private static final String PASSKEY_PREFIX = "Passkey: ";

    /**
     * Default constructor.
     *
     * @param driver      a {@link ChromeDriver} used to load web pages and perform UI actions
     * @param trackerUrls the URLs to the tracker
     */
    public CathodeRayTubeHandler(final ChromeDriver driver, final Collection<String> trackerUrls) {
        super(driver, trackerUrls);
    }

    @Override
    public void navigateToLoginPage() {
        final By loginLinkSelector = By.xpath("//a[text()='Login']");
        final WebElement loginLink = driver.findElement(loginLinkSelector);
        loginLink.click();
        ScriptExecutor.waitForElementToAppear(driver, usernameFieldSelector(), DEFAULT_WAIT_FOR_PAGE_LOAD);
    }

    @Override
    protected By usernameFieldSelector() {
        return By.xpath("//div[@id='username']//input[@name='username']");
    }

    @Override
    protected By passwordFieldSelector() {
        return By.xpath("//div[@id='password']//input[@name='password']");
    }

    @Override
    public By loginButtonSelector() {
        return By.id("login_button");
    }

    @Override
    protected By postLoginSelector() {
        return By.xpath("//table[contains(@class, 'userinfo_stats')]");
    }

    @Override
    protected By profilePageSelector() {
        return By.xpath("//a[@class='username']");
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * For {@link CathodeRayTubeHandler}, we also need to redact a passkey {@link WebElement}. We find an element with text that is prefixed by
     * {@value #PASSKEY_PREFIX}, signifying a {@link WebElement} with a sensitive passkey. We redact this element by replacing all text with the
     * prefix and {@value ScriptExecutor#DEFAULT_REDACTION_TEXT}.
     *
     * @see AbstractTrackerHandler#redactElements()
     * @see ScriptExecutor#redactInnerTextOf(JavascriptExecutor, WebElement, String)
     */
    @Override
    public int redactElements() {
        final int superRedactedElements = super.redactElements();

        final String passkeyRedactionText = PASSKEY_PREFIX + ScriptExecutor.DEFAULT_REDACTION_TEXT;
        final WebElement passkeyElement = driver.findElement(By.xpath(String.format("//li[contains(text(),'%s')]", PASSKEY_PREFIX)));
        ScriptExecutor.redactInnerTextOf(driver, passkeyElement, passkeyRedactionText);

        return superRedactedElements + 1;
    }

    @Override
    protected Collection<By> getElementsPotentiallyContainingSensitiveInformation() {
        return List.of(
            By.tagName("a"),
            By.tagName("span")
        );
    }

    @Override
    protected By logoutButtonSelector() {
        // Highlight the profile menu to make the logout button interactable
        final By logoutParentBy = By.id("userinfo_username");
        final WebElement logoutParent = driver.findElement(logoutParentBy);
        ScriptExecutor.moveTo(driver, logoutParent);

        return By.xpath("//li[@id='nav_logout']//a[text()='Logout']");
    }

    @Override
    protected By postLogoutElementSelector() {
        return By.xpath("//table[@id='maincontent']//a[text()='Login']");
    }
}
