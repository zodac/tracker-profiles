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
import java.util.Collection;
import me.zodac.tracker.framework.TrackerAccessibility;
import me.zodac.tracker.framework.TrackerDefinition;
import me.zodac.tracker.framework.TrackerHandlerType;
import me.zodac.tracker.util.ScreenshotTaker;
import me.zodac.tracker.util.ScriptExecutor;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;

/**
 * Implementation of {@link AbstractTrackerHandler} for the {@code Anthelion} tracker.
 */
@TrackerHandlerType(trackerName = "Anthelion", accessibility = TrackerAccessibility.PRIVATE)
public class AnthelionHandler extends AbstractTrackerHandler {

    private static final By LOGIN_ELEMENT_SELECTOR = By.xpath("//input[@type='submit' and @name='login' and @value='Log in' and @class='submit']");
    private static final By LOGOUT_ELEMENT_SELECTOR = By.xpath("//div[@id='user_menu']//a[text()='Logout']");

    /**
     * Default constructor.
     *
     * @param driver a {@link ChromeDriver} used to load web pages and perform UI actions
     */
    public AnthelionHandler(final ChromeDriver driver) {
        super(driver);
    }

    @Override
    public void openLoginPage(final TrackerDefinition trackerDefinition) {
        driver.navigate().to(trackerDefinition.loginLink());
        ScriptExecutor.waitForPageToLoad(driver, Duration.of(5, ChronoUnit.SECONDS));
    }

    @Override
    public void login(final TrackerDefinition trackerDefinition) {
        final WebElement username = driver.findElement(By.id("username"));
        username.clear();
        username.sendKeys(trackerDefinition.username());

        final WebElement password = driver.findElement(By.id("password"));
        password.clear();
        password.sendKeys(trackerDefinition.password());

        final WebElement loginButton = driver.findElement(LOGIN_ELEMENT_SELECTOR);
        loginButton.click();

        ScriptExecutor.waitForPageToLoad(driver, Duration.of(10, ChronoUnit.SECONDS));
    }

    @Override
    public void openProfilePage(final TrackerDefinition trackerDefinition) {
        driver.navigate().to(trackerDefinition.profilePage());
        ScriptExecutor.waitForPageToLoad(driver, Duration.of(5, ChronoUnit.SECONDS));
    }

    @Override
    public double zoomLevelForScreenshot() {
        return ScreenshotTaker.DEFAULT_ZOOM_LEVEL;
    }

    @Override
    public boolean canCookieBannerBeCleared() {
        return false;
    }

    @Override
    public Collection<WebElement> getElementsToBeMasked() {
        return driver
            .findElements(By.tagName("a"))
            .stream()
            .filter(this::doesElementContainEmailAddress)
            .toList();
    }

    @Override
    public void logout() {
        // Highlight the nav bar to make the logout button interactable
        final By logoutParentBy = By.id("nav_user");
        final WebElement logoutParent = driver.findElement(logoutParentBy);
        ScriptExecutor.moveTo(driver, logoutParent);

        final WebElement logoutButton = driver.findElement(LOGOUT_ELEMENT_SELECTOR);
        logoutButton.click();
        ScriptExecutor.waitForElementToAppear(driver, LOGIN_ELEMENT_SELECTOR, Duration.of(5, ChronoUnit.SECONDS));
    }
}
