/*
 * BSD Zero Clause License
 *
 * Copyright (c) 2024-2026 zodac.net
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
import java.io.IOException;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.NoSuchElementException;
import net.zodac.tracker.framework.exception.TranslationException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.Alert;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptException;
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
public final class BrowserInteractionHelper {

    private static final Duration DEFAULT_WAIT_FOR_ALERT = Duration.ofSeconds(2L);
    private static final Duration DEFAULT_WAIT_FOR_CONTEXT_MENU = Duration.of(500L, ChronoUnit.MILLIS);
    private static final Duration DEFAULT_WAIT_FOR_KEY_PRESS = Duration.of(250L, ChronoUnit.MILLIS);
    private static final Duration DEFAULT_WAIT_FOR_MOUSE_MOVE = Duration.of(300L, ChronoUnit.MILLIS);
    private static final Duration DEFAULT_WAIT_FOR_TRANSLATION = Duration.ofSeconds(2L);
    private static final Logger LOGGER = LogManager.getLogger();

    private final RemoteWebDriver driver;

    /**
     * Constructor that takes in a {@link RemoteWebDriver}.
     *
     * @param driver the {@link RemoteWebDriver}
     */
    // TODO: Static constructor
    public BrowserInteractionHelper(final RemoteWebDriver driver) {
        this.driver = driver;
    }

    /**
     * Finds an alert and accepts it.
     */
    public void acceptAlert() {
        final Wait<WebDriver> wait = new WebDriverWait(driver, DEFAULT_WAIT_FOR_ALERT)
            .ignoring(NoSuchElementException.class);
        final Alert alert = wait.until(ExpectedConditions.alertIsPresent());
        LOGGER.trace("Accepting alert pop-up");
        alert.accept();
    }

    /**
     * Adds a value to the HTML 'class' attribute of the {@link WebElement}. Does not override existing classes.
     *
     * @param element    the {@link WebElement}
     * @param classToAdd the name of the class to add to the {@link WebElement}
     */
    public void addClass(final WebElement element, final String classToAdd) {
        LOGGER.trace("Adding class '{}' to element '{}'", classToAdd, element);
        driver.executeScript("arguments[0].classList.add(arguments[1]);", element, classToAdd);
    }

    /**
     * Performs a {@link Thread#sleep(Duration)} for the specified {@link Duration}.
     *
     * @param sleepTime the time to wait
     * @param reason    the reason for sleeping
     */
    public static void explicitWait(final Duration sleepTime, final String reason) {
        try {
            LOGGER.trace("Sleeping for {}, waiting for {}", sleepTime, reason);
            Thread.sleep(sleepTime);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    /**
     * Retrieves the visible text content of the given {@link WebElement}. Attempts three sources in order, via a single
     * {@link JavascriptExecutor#executeScript} call:
     * <ol>
     *     <li>{@code innerText}, the standard rendered text (equivalent to {@link WebElement#getText()})</li>
     *     <li>The {@code value} DOM property, for {@code <input>} and similar form elements</li>
     *     <li>The {@code textContent} DOM property, for elements where rendered text is not exposed via the above</li>
     * </ol>
     *
     * @param element the {@link WebElement}
     * @return the first non-empty text value found, or an empty {@link String} if none are available
     */
    public String getTextContent(final WebElement element) {
        final Object result = driver.executeScript("""
            var e = arguments[0];
            var t = e.innerText || '';
            if (t !== '') {
                return t;
            }
            
            var v = e.value || '';
            if (v !== '') {
                return v;
            }
            
            return e.textContent || '';
            """, element);
        return result == null ? "" : String.valueOf(result);
    }

    /**
     * Hides the scrollbar on the current web page without changing page dimensions. Rather than setting {@code overflow: hidden} (which removes the
     * scrollbar gutter and causes layout shifts), this injects a stylesheet that makes the scrollbar transparent via both the standard
     * {@code scrollbar-color} property and the legacy {@code ::-webkit-scrollbar} pseudo-elements. The gutter space is preserved, preventing
     * redaction positions from shifting.
     */
    public void hideScrollbar() {
        LOGGER.trace("Hiding scrollbar on page without changing page dimensions");
        driver.executeScript("""
            var style = document.createElement('style');
            style.id = 'hide-scrollbar-style';
            style.textContent = \
                '* { scrollbar-color: transparent transparent !important; } \
                ::-webkit-scrollbar { background-color: transparent !important; } \
                ::-webkit-scrollbar-thumb { background-color: transparent !important; } \
                ::-webkit-scrollbar-track { background-color: transparent !important; }';
            document.head.appendChild(style);
            """);
    }

    /**
     * Highlight's an {@link WebElement} on the web page. Creates a 3px solid red border around the {@link WebElement}.
     *
     * @param element the {@link WebElement} to highlight
     */
    public void highlightElement(final WebElement element) {
        updateCss(element, "border", "3px solid red");
    }

    /**
     * Updates the {@link WebElement} and unfixes it from its pinned position. First all CSS transitions and animations are disabled, to avoid
     * scrolling triggering an update to the {@link WebElement}.
     *
     * @param element the {@link WebElement} to unfix
     */
    public void makeUnfixed(final WebElement element) {
        LOGGER.trace("Disabling CSS transitions and animations on page");
        driver.executeScript("""
            var style = document.createElement('style');
            style.id = 'disable-transitions-style';
            style.textContent = '*, *::before, *::after { transition: none !important; animation: none !important; }';
            document.head.appendChild(style);
            """);

        updateCss(element, "position", "static");
    }

    private void updateCss(final WebElement element, final String propertyName, final String propertyValue) {
        LOGGER.trace("Updating CSS of element '{}', property '{}' to value: '{}'", element, propertyName, propertyValue);
        driver.executeScript("arguments[0].style[arguments[1]] = arguments[2];", element, propertyName, propertyValue);
    }

    /**
     * Moves the mouse cursor to the provided {@link WebElement}.
     *
     * @param element the {@link WebElement} to move to
     */
    public void moveTo(final WebElement element) {
        LOGGER.trace("Moving cursor to element {}", element);
        final Actions actions = new Actions(driver);
        actions.moveToElement(element).perform();
        explicitWait(DEFAULT_WAIT_FOR_MOUSE_MOVE, "cursor to move to element");
    }

    /**
     * Moves the mouse cursor to the provided {@link WebElement}.
     *
     * @param x positive pixel value along horizontal axis in viewport (numbers increase going right)
     * @param y positive pixel value along vertical axis in viewport (numbers increase going down)
     */
    public void moveTo(final int x, final int y) {
        LOGGER.trace("Moving cursor to ({},{})", x, y);
        final Actions actions = new Actions(driver);
        actions.moveToLocation(x, y).perform();
        explicitWait(DEFAULT_WAIT_FOR_MOUSE_MOVE, "cursor to move to coordinates");
    }

    /**
     * Moves the mouse cursor the origin of the web page; the top-left corner.
     */
    public void moveToOrigin() {
        moveTo(0, 0);
    }

    /**
     * Remove an HTML attribute from the {@link WebElement}.
     *
     * @param element       the {@link WebElement} to update
     * @param attributeName the HTML attribute name
     */
    public void removeAttribute(final WebElement element, final String attributeName) {
        LOGGER.trace("Removing attribute '{}' from {}", attributeName, element);
        driver.executeScript("arguments[0].removeAttribute(arguments[1]);", element, attributeName);
    }

    /**
     * Remove an HTML {@link WebElement} from the page.
     *
     * @param element the {@link WebElement} to remove
     */
    public void removeElement(final WebElement element) {
        LOGGER.trace("Removing element {}", element);
        driver.executeScript("arguments[0].remove();", element);
    }

    /**
     * Scrolls the page by the provided number of pixels in both the X and Y axis.
     *
     * @param x pixels to scroll left/right
     * @param y pixels to scroll up/down
     */
    public void scroll(final int x, final int y) {
        LOGGER.trace("Scrolling up/down {}, left/right {}", x, y);
        driver.executeScript("window.scrollBy(arguments[0], arguments[1]);", x, y);
        explicitWait(Duration.ofMillis(100L), "page to scroll");
    }

    /**
     * Scrolls the page to the provided {@link WebElement}.
     *
     * @param element the {@link WebElement} to scroll to
     */
    public void scrollToElement(final WebElement element) {
        LOGGER.trace("Scrolling to {}", element);
        driver.executeScript("arguments[0].scrollIntoView(true);", element);
        explicitWait(Duration.ofMillis(250L), "page to scroll to the element");
    }

    /**
     * Scrolls the page back to the top of the screen.
     */
    public void scrollToTheTop() {
        driver.executeScript("window.scrollTo({top: 0, left: 0, behavior: 'instant'});");
    }

    /**
     * Some web pages may have 'overflow' set to 'hidden', which can disable scrolling. This function will override the configuration of the web page
     * to enable scrolling again.
     */
    public void showScrollbar() {
        LOGGER.trace("Enabling scrolling on page");

        try {
            driver.executeScript("""
                document.body.style.height = 'auto';
                document.body.style.overflowY = 'visible';
                var style = document.getElementById('hide-scrollbar-style');
                if (style) {
                    style.remove();
                }
                """);
        } catch (final JavascriptException e) {
            LOGGER.trace("Unable to show scrollbar", e);
        }
    }

    /**
     * Stops the loading of the current web page.
     */
    public void stopPageLoad() {
        LOGGER.trace("Stopping page load");
        driver.executeScript("window.stop();");
    }

    /**
     * Translates the web page into English. Specifies the {@code <body>} HTML element to be right-clicked.
     *
     * @see #translatePage(By)
     */
    public void translatePage() {
        translatePage(By.tagName("body"));
    }

    /**
     * Translates the web page into English. Performs the following actions:
     * <ol>
     *     <li>Loads a non-interactive element on the web page</li>
     *      <li>Performs a right-click</li>
     *      <li>Using {@link Robot}, performs 3 'UP' keyboard presses to highlight the 'Translate to English' option</li>
     *      <li>Presses 'ENTER'</li>
     * </ol>
     *
     * @param nonInteractiveElementSelector the {@link By} selector to a non-interactive {@link WebElement} to right-click
     */
    public void translatePage(final By nonInteractiveElementSelector) {
        LOGGER.trace("Translating page to English");
        setBrowserAsActiveWindow();

        try {
            // Find a non-interactive element to right-click
            final WebElement bodyElement = driver.findElement(nonInteractiveElementSelector);

            // Simulate right-click on the page, then wait for it to appear
            final Actions actions = new Actions(driver);
            actions.contextClick(bodyElement).perform();
            explicitWait(DEFAULT_WAIT_FOR_CONTEXT_MENU, "context menu to open");

            // Press "Up" key 3 times to select 'Translate to English' option from bottom of the menu
            final Robot robot = new Robot();
            for (int i = 0; i < 3; i++) {
                robot.keyPress(KeyEvent.VK_UP);
                robot.keyRelease(KeyEvent.VK_UP);
                explicitWait(DEFAULT_WAIT_FOR_KEY_PRESS, "key press to activate");
            }

            // Press "Enter" to select the "Translate to English" option
            robot.keyPress(KeyEvent.VK_ENTER);
            robot.keyRelease(KeyEvent.VK_ENTER);

            explicitWait(DEFAULT_WAIT_FOR_TRANSLATION, "translation to complete");
        } catch (final AWTException e) {
            throw new TranslationException(e);
        }
    }

    private static void setBrowserAsActiveWindow() {
        LOGGER.trace("Setting Chromium browser as active window");
        try (final Process process = new ProcessBuilder("/usr/bin/wmctrl", "-x", "-a", "Chromium").start()) {
            final int exitValue = process.waitFor();
            LOGGER.trace("Process result: {}", exitValue);
        } catch (final IOException e) {
            LOGGER.debug("Error setting browser as active window", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.debug("Error setting browser as active window", e);
        }
    }

    /**
     * Removes the {@link WebElement} from the DOM while keeping all of its children in place.
     *
     * @param element the {@link WebElement} to unwrap
     */
    public void unwrapElement(final WebElement element) {
        LOGGER.trace("Unwrapping element '{}'", element);
        driver.executeScript("arguments[0].replaceWith(...arguments[0].childNodes);", element);
    }

    /**
     * Waits for the specified {@link WebElement} to become interactable.
     *
     * @param selector the {@link By} selector for the target {@link WebElement}
     * @param timeout  the maximum {@link Duration} to wait
     * @return the {@link WebElement} if clickable
     * @throws TimeoutException thrown if the {@link WebElement} doesn't become interactable in the specified {@link Duration}
     */
    public WebElement waitForElementToBeInteractable(final By selector, final Duration timeout) {
        LOGGER.trace("Waiting {} for [{}] to be interactable", timeout, selector);
        final Wait<WebDriver> wait = new WebDriverWait(driver, timeout);
        return wait.until(ExpectedConditions.elementToBeClickable(selector));
    }

    /**
     * Waits for the page that the {@link WebDriver} is loading to find the wanted {@link WebElement}.
     *
     * @param selector the {@link By} selector for the target {@link WebElement}
     * @param timeout  the maximum {@link Duration} to wait
     * @return the {@link WebElement} if found
     * @throws TimeoutException thrown if the {@link WebElement} doesn't load in the specified {@link Duration}
     */
    public WebElement waitForElementToBePresent(final By selector, final Duration timeout) {
        LOGGER.trace("Waiting {} for [{}] to appear", timeout, selector);
        final Wait<WebDriver> wait = new WebDriverWait(driver, timeout);
        return wait.until(ExpectedConditions.presenceOfElementLocated(selector));
    }

    /**
     * Waits for the page that the {@link WebDriver} is loading to find the wanted {@link WebElement} and ensure it is visible.
     *
     * @param selector the {@link By} selector for the target {@link WebElement}
     * @param timeout  the maximum {@link Duration} to wait
     * @throws TimeoutException thrown if the {@link WebElement} doesn't become visible in the specified {@link Duration}
     */
    public void waitForElementToBeVisible(final By selector, final Duration timeout) {
        LOGGER.trace("Waiting {} for [{}] to be visible", timeout, selector);
        final Wait<WebDriver> wait = new WebDriverWait(driver, timeout);
        wait.until(ExpectedConditions.visibilityOfElementLocated(selector));
    }

    /**
     * Waits for the specified {@link WebElement} to become interactable.
     *
     * @param selector the {@link By} selector for the target {@link WebElement}
     * @param timeout  the maximum {@link Duration} to wait
     * @throws TimeoutException thrown if the {@link WebElement} doesn't become interactable in the specified {@link Duration}
     */
    public void waitForElementToDisappear(final By selector, final Duration timeout) {
        LOGGER.trace("Waiting {} for [{}] to disappear", timeout, selector);
        final Wait<WebDriver> wait = new WebDriverWait(driver, timeout);
        wait.until(ExpectedConditions.invisibilityOfElementLocated(selector));
    }

    /**
     * Waits for the page that the {@link RemoteWebDriver} is loading to completely load. If the {@code timeout} {@link Duration} is exceeded, the
     * execution will continue.
     *
     * @param timeout the maximum {@link Duration} to wait
     */
    public void waitForPageToLoad(final Duration timeout) {
        try {
            LOGGER.trace("Waiting {} for page to load", timeout);
            final Wait<WebDriver> wait = new WebDriverWait(driver, timeout)
                .withMessage("Waiting for page to load");
            wait.until(_ -> "complete".equals(driver.executeScript("return document.readyState")));
        } catch (final TimeoutException e) {
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("Page didn't load, page source: {}", driver.getPageSource());
            }
            throw e;
        }
    }
}
