package me.zodac.tracker.framework;

import java.util.List;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

public abstract class TrackerHandler {

    protected static final String HOST_IP_ADDRESS = System.getProperty("HOST_IP_ADDRESS", System.getenv("HOST_IP_ADDRESS"));
    protected static final String EMAIL_ADDRESS = System.getProperty("EMAIL_ADDRESS", System.getenv("EMAIL_ADDRESS"));

    public abstract void openLoginPage(final WebDriver driver, final TrackerInfo trackerInfo);

    public abstract void login(final WebDriver driver, final TrackerInfo trackerInfo);

    public abstract void openProfilePage(final WebDriver driver, final TrackerInfo trackerInfo);

    public abstract boolean canCookieBannerBeCleared(final WebDriver driver);

    public abstract List<WebElement> getElementsToBeMasked(final WebDriver driver);
}
