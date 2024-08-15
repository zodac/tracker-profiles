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

package me.zodac.tracker.handler;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import me.zodac.tracker.framework.TrackerAccessibility;
import me.zodac.tracker.framework.TrackerDefinition;
import me.zodac.tracker.framework.TrackerHandler;
import me.zodac.tracker.framework.TrackerHandlerType;
import me.zodac.tracker.util.ScriptExecutor;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.interactions.Actions;

/**
 * Implementation of {@link TrackerHandler} for the {@code ATH} tracker.
 */
@TrackerHandlerType(trackerCode = "ATH", accessibility = TrackerAccessibility.PRIVATE)
public class AthHandler extends TrackerHandler {

    /**
     * Default constructor.
     *
     * @param driver a {@link ChromeDriver} used to load web pages and perform UI actions
     */
    public AthHandler(final ChromeDriver driver) {
        super(driver);
    }

    @Override
    public void openLoginPage(final TrackerDefinition trackerDefinition) {
        driver.navigate().to(trackerDefinition.loginLink());
    }

    @Override
    public void login(final TrackerDefinition trackerDefinition) {
        final WebElement username = driver.findElement(By.id("username"));
        username.clear();
        username.sendKeys(trackerDefinition.username());

        final WebElement password = driver.findElement(By.id("password"));
        password.clear();
        password.sendKeys(trackerDefinition.password());

        final WebElement loginButton = driver.findElement(By.tagName("button"));
        loginButton.click();

        ScriptExecutor.waitForPageToLoad(driver, Duration.of(10, ChronoUnit.SECONDS));
    }

    @Override
    public void openProfilePage(final TrackerDefinition trackerDefinition) {
        driver.navigate().to(trackerDefinition.profilePage());
        ScriptExecutor.waitForPageToLoad(driver, Duration.of(5, ChronoUnit.SECONDS));
    }

    @Override
    public boolean canCookieBannerBeCleared() {
        final WebElement alertElement = driver.findElement(By.className("alerts"));
        final WebElement cookieButton = alertElement.findElement(By.tagName("button"));
        cookieButton.click();

        // Move the mouse, or else a dropdown menu is highlighted and covers some of the page
        final Actions actions = new Actions(driver);
        actions.moveToLocation(0, 0).perform();
        return true;
    }

    @Override
    public Collection<WebElement> getElementsToBeMasked() {
        final Collection<WebElement> elementsToBeMasked = new ArrayList<>();
        elementsToBeMasked.addAll(ipAddressElements());
        elementsToBeMasked.addAll(emailAddressElements());
        return elementsToBeMasked;
    }

    private List<WebElement> ipAddressElements() {
        return driver
            .findElements(By.tagName("td"))
            .stream()
            .filter(element -> element.getText().contains(HOST_IP_ADDRESS))
            .toList();
    }

    private List<WebElement> emailAddressElements() {
        return driver
            .findElements(By.tagName("dd"))
            .stream()
            .filter(element -> element.getText().contains(EMAIL_ADDRESS))
            .toList();
    }
}
