package me.zodac.tracker.framework;

import java.util.List;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import me.zodac.tracker.util.TrackerInfo;

public abstract class TrackerHandler {

    public abstract void openLoginPage(final WebDriver driver, final TrackerInfo trackerInfo);

    public abstract void login(final WebDriver driver, final TrackerInfo trackerInfo);

    public abstract void openProfilePage(final WebDriver driver, final TrackerInfo trackerInfo);

    public abstract boolean canCookieBannerBeCleared(final WebDriver driver);

    public abstract List<WebElement> getElementsToBeMasked(final WebDriver driver);
}
