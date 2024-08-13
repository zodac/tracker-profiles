package me.zodac.tracker.handler;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import me.zodac.tracker.framework.TrackerHandler;
import me.zodac.tracker.framework.TrackerType;
import me.zodac.tracker.util.ScriptExecutor;
import me.zodac.tracker.framework.TrackerInfo;
import org.openqa.selenium.By;
import org.openqa.selenium.SearchContext;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Actions;

@TrackerType(trackerName = "Aither")
public class AitherHandler extends TrackerHandler {

    @Override
    public void openLoginPage(final WebDriver driver, final TrackerInfo trackerInfo) {
        driver.navigate().to(trackerInfo.loginLink());
    }

    @Override
    public void login(final WebDriver driver, final TrackerInfo trackerInfo) {
        final WebElement username = driver.findElement(By.id("username"));
        username.clear();
        username.sendKeys(trackerInfo.username());

        final WebElement password = driver.findElement(By.id("password"));
        password.clear();
        password.sendKeys(trackerInfo.password());

        final WebElement loginButton = driver.findElement(By.tagName("button"));
        loginButton.click();

        ScriptExecutor.waitForPageToLoad(driver, Duration.of(10, ChronoUnit.SECONDS));
    }

    public void openProfilePage(final WebDriver driver, final TrackerInfo trackerInfo) {
        driver.navigate().to(trackerInfo.profilePage());
        ScriptExecutor.waitForPageToLoad(driver, Duration.of(5, ChronoUnit.SECONDS));
    }

    public boolean canCookieBannerBeCleared(final WebDriver driver) {
        final WebElement alertElement = driver.findElement(By.className("alerts"));
        final WebElement cookieButton = alertElement.findElement(By.tagName("button"));
        cookieButton.click();

        // Move the mouse, or else a dropdown menu is highlighted and covers some of the page
        final Actions actions = new Actions(driver);
        actions.moveToLocation(0, 0).perform();
        return true;
    }

    public List<WebElement> getElementsToBeMasked(final WebDriver driver) {
        final List<WebElement> elementsToBeMasked = new ArrayList<>();
        elementsToBeMasked.addAll(ipAddressElements(driver));
        elementsToBeMasked.addAll(emailAddressElements(driver));
        return elementsToBeMasked;
    }

    private static List<WebElement> ipAddressElements(final SearchContext driver) {
        return driver
            .findElements(By.tagName("td"))
            .stream()
            .filter(element -> element.getText().contains(HOST_IP_ADDRESS))
            .toList();
    }

    private static List<WebElement> emailAddressElements(final SearchContext driver) {
        return driver
            .findElements(By.tagName("dd"))
            .stream()
            .filter(element -> element.getText().contains(EMAIL_ADDRESS))
            .toList();
    }
}
