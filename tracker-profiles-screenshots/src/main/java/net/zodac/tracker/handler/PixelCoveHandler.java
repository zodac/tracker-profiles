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
import net.zodac.tracker.framework.TrackerAccessibility;
import net.zodac.tracker.framework.TrackerHandlerType;
import net.zodac.tracker.util.ScriptExecutor;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;

/**
 * Implementation of {@link AbstractTrackerHandler} for the {@code PixelCove} tracker.
 */
@TrackerHandlerType(trackerName = "PixelCove", accessibility = TrackerAccessibility.PRIVATE)
public class PixelCoveHandler extends AbstractTrackerHandler {

    private static final String PASSKEY_PREFIX = "Passkey: ";
    private static final double ZOOM_LEVEL_FOR_SCREENSHOT = 0.9D;

    /**
     * Default constructor.
     *
     * @param driver a {@link ChromeDriver} used to load web pages and perform UI actions
     */
    public PixelCoveHandler(final ChromeDriver driver) {
        super(driver);
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
    public double zoomLevel() {
        return ZOOM_LEVEL_FOR_SCREENSHOT;
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * For {@link PixelCoveHandler}, we also need to redact a passkey {@link WebElement}. We find an element with text that is prefixed by
     * {@value #PASSKEY_PREFIX}, signifying a {@link WebElement} with a sensitive passkey. We redact this element by replacing all text with the
     * prefix and {@value ScriptExecutor#DEFAULT_REDACTION_TEXT}.
     *
     * @see ScriptExecutor#redactInnerTextOf(JavascriptExecutor, WebElement, String)
     * @see AbstractTrackerHandler#redactElements()
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
        // Highlight the nav bar to make the logout button interactable
        final By logoutParentBy = By.xpath("//li[@id='userinfo_username']//a[contains(@class, 'username')]");
        final WebElement logoutParent = driver.findElement(logoutParentBy);
        ScriptExecutor.moveTo(driver, logoutParent);

        return By.xpath("//a[contains(text(), 'Logout')]");
    }

    @Override
    protected By postLogoutElementSelector() {
        return By.xpath("//table[@id='maincontent']//a[text()='Login']");
    }
}
