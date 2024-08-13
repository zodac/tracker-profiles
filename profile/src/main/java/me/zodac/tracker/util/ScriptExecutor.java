package me.zodac.tracker.util;

import java.time.Duration;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.Wait;
import org.openqa.selenium.support.ui.WebDriverWait;

public final class ScriptExecutor {

    private static final String MASK_VALUE = "----";

    private ScriptExecutor() {

    }

    public static void maskInnerTextOfElement(final JavascriptExecutor driver, final WebElement element) {
        driver.executeScript(String.format("arguments[0].innerText = '%s'", MASK_VALUE), element);
    }

    public static void waitForPageToLoad(final WebDriver driver, final Duration timeout) {
        final Wait<WebDriver> wait = new WebDriverWait(driver, timeout);
        wait.until(_ -> ((JavascriptExecutor) driver).executeScript("return document.readyState").equals("complete"));
    }
}
