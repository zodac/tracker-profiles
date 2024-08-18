/*
 * BSD Zero Clause License
 *
 * Copyright (c) 2021-2024 zodac.me
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

package me.zodac.tracker.util;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Wait;
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 * Utility class used to execute scripts on a web page.
 */
public final class ScriptExecutor {

    /**
     * Default {@link String} used to redact sensisitve text.
     */
    public static final String DEFAULT_REDACTION_TEXT = "----";

    private static final Duration DEFAULT_WAIT_FOR_MOUSE_MOVE = Duration.of(250L, ChronoUnit.MILLIS);

    private ScriptExecutor() {

    }

    /**
     * Performs a {@link Thread#sleep(Duration)} for the supplied {@link Duration}.
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
     * Updates the text of the provided {@link WebElement} and replaces the value with {@value #DEFAULT_REDACTION_TEXT}. This can be valuable when
     * trying to hide/redact sentitive information like IP addresses.
     *
     * @param driver  the {@link JavascriptExecutor} with the loaded web page
     * @param element the {@link WebElement} to redact
     * @see #redactInnerTextOf(JavascriptExecutor, WebElement, String)
     */
    public static void redactInnerTextOf(final JavascriptExecutor driver, final WebElement element) {
        redactInnerTextOf(driver, element, DEFAULT_REDACTION_TEXT);
    }

    /**
     * Updates the text of the provided {@link WebElement} and replaces the value with {@code #redactionText}. This can be valuable when trying to
     * hide/redact sentitive information like IP addresses.
     *
     * @param driver        the {@link JavascriptExecutor} with the loaded web page
     * @param element       the {@link WebElement} to redact
     * @param redactionText the text to replace the existing text in the {@link WebElement}
     */
    public static void redactInnerTextOf(final JavascriptExecutor driver, final WebElement element, final String redactionText) {
        driver.executeScript(String.format("arguments[0].innerText = '%s'", redactionText), element);
    }

    /**
     * Moves the mouse cursor to the provided {@link WebElement}.
     *
     * @param driver  the {@link JavascriptExecutor} with the loaded web page
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
     * @param driver the {@link JavascriptExecutor} with the loaded web page
     */
    public static void moveToOrigin(final WebDriver driver) {
        final Actions actions = new Actions(driver);
        actions.moveToLocation(0, 0).perform();
        explicitWait(DEFAULT_WAIT_FOR_MOUSE_MOVE);
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
        final Wait<WebDriver> wait = new WebDriverWait(driver, timeout);
        wait.until(ExpectedConditions.presenceOfAllElementsLocatedBy(selector));
    }

    /**
     * Waits for the page that the {@link WebDriver} is loading to completely load. If the {@code timeout} {@link Duration} is exceeded, the execution
     * will continue.
     *
     * @param driver  the {@link WebDriver} with the loaded web page
     * @param timeout the maximum {@link Duration} to wait
     */
    public static void waitForPageToLoad(final WebDriver driver, final Duration timeout) {
        final Wait<WebDriver> wait = new WebDriverWait(driver, timeout);
        wait.until(_ -> "complete".equals(((JavascriptExecutor) driver).executeScript("return document.readyState")));
    }

    /**
     * Zooms in/out on the currently loaded web page. Uses the {@code zoomLevel} as a decimal format of the zoom (<b>1.0</b> for 100%, <b>0.75</b> for
     * 75%, etc.)
     *
     * @param driver    the {@link JavascriptExecutor} with the loaded web page
     * @param zoomLevel the zoom level
     */
    public static void zoom(final JavascriptExecutor driver, final double zoomLevel) {
        driver.executeScript(String.format("document.body.style.zoom = '%.2f'", zoomLevel));
    }
}
