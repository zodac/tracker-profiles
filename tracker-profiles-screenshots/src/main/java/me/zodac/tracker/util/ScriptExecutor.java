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
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.Wait;
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 * Utility class used to execute scripts on a web page.
 */
public final class ScriptExecutor {

    private static final String MASK_VALUE = "----";

    private ScriptExecutor() {

    }

    /**
     * Updates the text of the provided {@link WebElement} and replaces the value with {@value #MASK_VALUE}. This can be valuable when trying to
     * hide/redact sentitive information like IP addresses.
     *
     * @param driver  the {@link JavascriptExecutor} with the loaded web page
     * @param element the {@link WebElement} to mask
     */
    public static void maskInnerTextOfElement(final JavascriptExecutor driver, final WebElement element) {
        driver.executeScript(String.format("arguments[0].innerText = '%s'", MASK_VALUE), element);
    }

    /**
     * Waits for the page that the {@link WebDriver} is loaded to completely load. If the {@code timeout} {@link Duration} is exceeded, the execution
     * will continue.
     *
     * @param driver  the {@link WebDriver} with the loaded web page
     * @param timeout the maximum {@link Duration} to wait
     */
    public static void waitForPageToLoad(final WebDriver driver, final Duration timeout) {
        final Wait<WebDriver> wait = new WebDriverWait(driver, timeout);
        wait.until(_ -> "complete".equals(((JavascriptExecutor) driver).executeScript("return document.readyState")));
    }
}
