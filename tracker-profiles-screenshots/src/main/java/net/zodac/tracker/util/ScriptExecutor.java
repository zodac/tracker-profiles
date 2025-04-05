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

package net.zodac.tracker.util;

import java.awt.AWTException;
import java.awt.Robot;
import java.awt.event.KeyEvent;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.NoSuchElementException;
import java.util.regex.Pattern;
import net.zodac.tracker.framework.Configuration;
import net.zodac.tracker.framework.ConfigurationProperties;
import net.zodac.tracker.framework.exception.TranslationException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jspecify.annotations.Nullable;
import org.openqa.selenium.Alert;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Wait;
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 * Utility class used to execute scripts on a web page.
 */
// TODO: Change to a class that takes in a driver, to avoid passing it in so much, and use in AbstractTrackerHandler?
public final class ScriptExecutor {

    /**
     * Default {@link String} used to redact sensitive text.
     */
    public static final String DEFAULT_REDACTION_TEXT = "----";

    private static final Duration DEFAULT_WAIT_FOR_ALERT = Duration.of(2L, ChronoUnit.SECONDS);
    private static final Duration DEFAULT_WAIT_FOR_CONTEXT_MENU = Duration.of(500L, ChronoUnit.MILLIS);
    private static final Duration DEFAULT_WAIT_FOR_KEY_PRESS = Duration.of(250L, ChronoUnit.MILLIS);
    private static final Duration DEFAULT_WAIT_FOR_MOUSE_MOVE = Duration.of(1L, ChronoUnit.SECONDS);
    private static final Duration DEFAULT_WAIT_FOR_PAGE_LOAD = Duration.of(1L, ChronoUnit.SECONDS);
    private static final Duration DEFAULT_WAIT_FOR_TRANSLATION = Duration.of(5000L, ChronoUnit.MILLIS);
    private static final Pattern NEWLINE_PATTERN = Pattern.compile("\\r?\\n");
    private static final Logger LOGGER = LogManager.getLogger();

    private ScriptExecutor() {

    }

    /**
     * Finds a Chrome alert and accepts it.
     *
     * @param driver the {@link WebDriver} with the loaded web page
     */
    public static void acceptAlert(final WebDriver driver) {
        final Wait<WebDriver> wait = new WebDriverWait(driver, DEFAULT_WAIT_FOR_ALERT)
            .ignoring(NoSuchElementException.class);
        final Alert alert = wait.until(ExpectedConditions.alertIsPresent());
        alert.accept();
    }

    /**
     * Disables scrolling on the current webpage, to remove the scrollbar from the screenshot.
     *
     * @param driver the {@link JavascriptExecutor} with the loaded web page
     */
    public static void disableScrolling(final JavascriptExecutor driver) {
        driver.executeScript("document.body.style.overflow = 'hidden'");
    }

    /**
     * Some web pages may have 'overflow' set to 'hidden', which can disable scrolling. This function will override the configuration of the web page
     * to enable scrolling again.
     *
     * @param driver            the {@link JavascriptExecutor} with the loaded web page
     * @param elementToOverride the element that needs to be overridden to allow scrolling (usually 'body')
     */
    public static void enableScrolling(final JavascriptExecutor driver, final String elementToOverride) {
        driver.executeScript(String.format("document.%s.style.height = 'auto';", elementToOverride));
        driver.executeScript(String.format("document.%s.style.overflowY = 'visible';", elementToOverride));
    }

    /**
     * Performs a {@link Thread#sleep(Duration)} for the specified {@link Duration}.
     *
     * @param sleepTime the time to wait
     */
    public static void explicitWait(final Duration sleepTime) {
        try {
            Thread.sleep(sleepTime);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    /**
     * Highlight's an {@link WebElement} on the web page. Creates a 3px solid red border around the {@link WebElement}.
     *
     * @param driver  the {@link JavascriptExecutor} with the loaded web page
     * @param element the {@link WebElement} to highlight
     */
    public static void highlightElement(final JavascriptExecutor driver, final WebElement element) {
        updateCss(driver, element, "border", "3px solid red");
    }

    /**
     * Moves the mouse cursor to the provided {@link WebElement}.
     *
     * @param driver  the {@link WebDriver} with the loaded web page
     * @param element the {@link WebElement} to move to
     */
    public static void moveTo(final WebDriver driver, final WebElement element) {
        final Actions actions = new Actions(driver);
        actions.moveToElement(element).perform();
        explicitWait(DEFAULT_WAIT_FOR_MOUSE_MOVE);
    }

    /**
     * Moves the mouse cursor the origin of the web page; the top-left corner.
     *
     * @param driver the {@link WebDriver} with the loaded web page
     */
    public static void moveToOrigin(final WebDriver driver) {
        final Actions actions = new Actions(driver);
        actions.moveToLocation(0, 0).perform();
        explicitWait(DEFAULT_WAIT_FOR_MOUSE_MOVE);
    }

    /**
     * Reads the text of the provided {@link WebElement} and replaces the {@link ConfigurationProperties#ipAddresses()} and
     * {@link ConfigurationProperties#emailAddresses()} with {@value #DEFAULT_REDACTION_TEXT}. This can be valuable when trying to hide/redact
     * sensitive information like IP addresses. This will attempt to retain all other text in the provided {@link WebElement}.
     *
     * @param driver  the {@link JavascriptExecutor} with the loaded web page
     * @param element the {@link WebElement} to redact
     */
    public static void redactInnerTextOf(final JavascriptExecutor driver, final WebElement element) {
        LOGGER.info("\t\t- Found: '{}' in <{}>", NEWLINE_PATTERN.matcher(element.getText()).replaceAll(""), element.getTagName());
        driver.executeScript(String.format("arguments[0].innerText = '%s'", createSubstitutionText(element)), element);
    }

    private static String createSubstitutionText(final WebElement element) {
        String substitutionText = element.getDomProperty("textContent");
        if (substitutionText == null) {
            return "";
        }

        for (final String ipAddress : Configuration.get().ipAddresses()) {
            substitutionText = substitutionText.replace(ipAddress, DEFAULT_REDACTION_TEXT);
        }
        for (final String emailAddress : Configuration.get().emailAddresses()) {
            substitutionText = substitutionText.replace(emailAddress, DEFAULT_REDACTION_TEXT);
        }
        return escapeForJavaScriptString(substitutionText);
    }

    private static String escapeForJavaScriptString(final String input) {
        return input
            .replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\"", "\\\"")
            .replace("\r", "")
            .replace("\n", "\\n");
    }

    /**
     * Updates the text of the provided {@link WebElement} and replaces the value with {@code #redactionText}. This can be valuable when trying to
     * hide/redact sensitive information like IP addresses.
     *
     * @param driver        the {@link JavascriptExecutor} with the loaded web page
     * @param element       the {@link WebElement} to redact
     * @param redactionText the text to replace the existing text in the {@link WebElement}
     */
    public static void redactInnerTextOf(final JavascriptExecutor driver, final WebElement element, final String redactionText) {
        LOGGER.info("\t\t- Found: '{}' in <{}>", NEWLINE_PATTERN.matcher(element.getText()).replaceAll(""), element.getTagName());
        driver.executeScript(String.format("arguments[0].innerText = '%s'", redactionText), element);
    }

    /**
     * Remove an HTML attribute from the {@link WebElement}.
     *
     * @param driver        the {@link JavascriptExecutor} with the loaded web page
     * @param element       the {@link WebElement} to update
     * @param attributeName the HTML attribute name
     */
    public static void removeAttribute(final JavascriptExecutor driver, final WebElement element, final String attributeName) {
        final String script = String.format("arguments[0].removeAttribute('%s');", attributeName);
        driver.executeScript(script, element);
    }

    /**
     * Scrolls the page back to the top of the screen.
     *
     * @param driver the {@link WebDriver} with the loaded web page
     */
    public static void scrollToTheTop(final JavascriptExecutor driver) {
        driver.executeScript("window.scrollTo(0, 0);");
        explicitWait(Duration.ofSeconds(1L)); // Wait 1 second to scroll back to the top
    }

    /**
     * Stops the loading of the current web page.
     *
     * @param driver the {@link WebDriver} with the loaded web page
     */
    public static void stopPageLoad(final JavascriptExecutor driver) {
        driver.executeScript("window.stop();");
    }

    /**
     * Translates the web page into English. Performs the following actions:
     * <ol>
     *     <li>Loads a non-interactive element on the web page</li>
     *      <li>Performs a right-click</li>
     *      <li>Using {@link Robot}, performs 3 'UP' keyboard presses to highlight the 'Translate to English' option</li>
     *      <li>Presses 'ENTER'</li>
     *      <li>Optionally resets the username {@link WebElement} that may have been incorrectly translated</li>
     * </ol>
     *
     * @param driver                the {@link RemoteWebDriver} with the loaded web page
     * @param username              the username
     * @param mistranslatedUsername the text (can be partial text) that the username is incorrectly translated into, in order to find and replace it
     */
    public static void translatePage(final RemoteWebDriver driver, final String username, final @Nullable String mistranslatedUsername) {
        try {
            // Find a non-interactive element to right-click
            final WebElement bodyElement = driver.findElement(By.tagName("body"));

            // Simulate right-click on the page, then wait for it to appear
            final Actions actions = new Actions(driver);
            actions.contextClick(bodyElement).perform();
            explicitWait(DEFAULT_WAIT_FOR_CONTEXT_MENU);

            // Press "Up" key 3 times to select 'Translate to English' option from bottom of the menu
            final Robot robot = new Robot();
            final int numberOfUpPressesToSelectTranslateButton = 3;
            for (int i = 0; i < numberOfUpPressesToSelectTranslateButton; i++) {
                robot.keyPress(KeyEvent.VK_UP);
                robot.keyRelease(KeyEvent.VK_UP);
                explicitWait(DEFAULT_WAIT_FOR_KEY_PRESS);
            }

            // Press "Enter" to select the "Translate to English" option
            robot.keyPress(KeyEvent.VK_ENTER);
            robot.keyRelease(KeyEvent.VK_ENTER);

            explicitWait(DEFAULT_WAIT_FOR_TRANSLATION);

            // After translation, some username elements will have been incorrectly translated
            final By mistranslatedElementSelector = By.xpath(String.format("//*[contains(text(), '%s')]", mistranslatedUsername));
            for (final WebElement element : driver.findElements(mistranslatedElementSelector)) {
                driver.executeScript(String.format("arguments[0].innerText = '%s'", username), element);
            }

            explicitWait(DEFAULT_WAIT_FOR_TRANSLATION);
        } catch (final AWTException e) {
            throw new TranslationException(e);
        }
    }

    /**
     * Updates the provided CSS property for the {@link WebElement}.
     *
     * @param driver        the {@link JavascriptExecutor} with the loaded web page
     * @param element       the {@link WebElement} whose CSS property should be updated
     * @param propertyName  the CSS property name
     * @param propertyValue the new CSS property value
     */
    public static void updateCss(final JavascriptExecutor driver, final WebElement element, final String propertyName, final String propertyValue) {
        final String script = String.format("arguments[0].style.%s = '%s';", propertyName, propertyValue);
        driver.executeScript(script, element);
    }

    /**
     * Waits for the page that the {@link WebDriver} is loading to find the wanted {@link WebElement}. If the {@code timeout} {@link Duration} is
     * exceeded, the execution will continue.
     *
     * @param driver   the {@link WebDriver} with the loaded web page
     * @param selector the {@link By} selector for the wanted {@link WebElement}
     * @param timeout  the maximum {@link Duration} to wait
     */
    public static void waitForElementToAppear(final WebDriver driver, final By selector, final Duration timeout) {
        try {
            final Wait<WebDriver> wait = new WebDriverWait(driver, timeout);
            wait.until(ExpectedConditions.presenceOfAllElementsLocatedBy(selector));
        } catch (final TimeoutException e) {
            LOGGER.debug(driver.getPageSource());
            throw e;
        }
    }

    /**
     * Waits for the page that the {@link RemoteWebDriver} is loading to completely load. If the {@code timeout} {@link Duration} is exceeded, the
     * execution will continue.
     *
     * @param driver  the {@link RemoteWebDriver} with the loaded web page
     * @param timeout the maximum {@link Duration} to wait
     */
    public static void waitForPageToLoad(final RemoteWebDriver driver, final Duration timeout) {
        explicitWait(DEFAULT_WAIT_FOR_PAGE_LOAD);

        try {
            final Wait<WebDriver> wait = new WebDriverWait(driver, timeout);
            wait.until(_ -> "complete".equals(driver.executeScript("return document.readyState")));
        } catch (final TimeoutException e) {
            LOGGER.debug(driver.getPageSource());
            throw e;
        }
    }
}
