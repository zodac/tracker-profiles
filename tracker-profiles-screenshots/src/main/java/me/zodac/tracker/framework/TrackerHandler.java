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

package me.zodac.tracker.framework;

import java.util.Collection;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;

/**
 * Abstract class used to define a {@link TrackerHandler}. All implementations will be used by {@link me.zodac.tracker.ProfileScreenshotter}, if the
 * tracker is included in the {@code trackers.csv} input file. This class lists the high-level methods required for
 * {@link me.zodac.tracker.ProfileScreenshotter} to be able to successfully generate a screenshot for a given tracker.
 *
 * <p>
 * Since each tracker website has its own UI and own page structure, each implementation of {@link TrackerHandler} will contain the tracker-specific
 * {@code selenium} logic to perform the UI actions.
 */
public abstract class TrackerHandler {

    /**
     * The {@link ConfigurationProperties} for the system.
     */
    protected static final ConfigurationProperties CONFIG = Configuration.get();

    /**
     * The {@link ChromeDriver} instance used to load web pages and perform UI actions.
     */
    protected ChromeDriver driver;

    /**
     * Default constructor, only for implementation classes.
     *
     * @param driver a {@link ChromeDriver} used to load web pages and perform UI actions
     */
    protected TrackerHandler(final ChromeDriver driver) {
        this.driver = driver;
    }

    /**
     * Navigates to the login page of the tracker.
     *
     * @param trackerDefinition the {@link TrackerDefinition} containing the login page URL
     */
    public abstract void openLoginPage(TrackerDefinition trackerDefinition);

    /**
     * Enters the user's credential and logs in to the tracker. Ideally, this method will wait until the redirect page after login has fully loaded.
     *
     * @param trackerDefinition the {@link TrackerDefinition} containing the login credentials
     */
    public abstract void login(TrackerDefinition trackerDefinition);

    /**
     * Once logged in, navigates to the user's profile page on the tracker.
     *
     * @param trackerDefinition the {@link TrackerDefinition} containing the user's profile URL
     */
    public abstract void openProfilePage(TrackerDefinition trackerDefinition);

    /**
     * Defines the zoom percentage required for the trakcer in order or all relevant details to be shown on the profile page and correctly screenshot.
     *
     * @return the zoom level required for the {@link TrackerHandler}
     * @see me.zodac.tracker.util.ScriptExecutor#zoom(JavascriptExecutor, double)
     */
    public abstract double zoomLevelForScreenshot();

    /**
     * Checks if there is a cookie banner on the profile page, and clicks it.
     *
     * @return {@code true} if there was a cookie banner, and it was cleared
     */
    public abstract boolean canCookieBannerBeCleared();

    /**
     * Returns a {@link Collection} of {@link WebElement}s from the user's profile page, where the inner text needs to be masked. This is used for
     * {@link WebElement}s that has sensitive information (like an IP address), which should not be visible in the screenshot.
     *
     * @return a {@link Collection} of {@link WebElement}s where the text needs to be masked
     * @see me.zodac.tracker.util.ScriptExecutor#maskInnerTextOfElement(JavascriptExecutor, WebElement)
     */
    public abstract Collection<WebElement> getElementsToBeMasked();

    /**
     * Logs out of the tracker, ending the user's session.
     */
    public abstract void logout();

    /**
     * Function that checks the {@link WebElement} to see if the {@link WebElement#getText()} contains any of the configured
     * {@link ConfigurationProperties#emailAddresses()}.
     *
     * @param element the {@link WebElement} to check
     * @return {@code true} if the {@link WebElement#getText()} contains any of the {@link ConfigurationProperties#emailAddresses()}
     */
    protected boolean doesElementContainEmailAddress(final WebElement element) {
        return doesElementContainAnyProvideString(element, CONFIG.emailAddresses());
    }

    /**
     * Function that checks the {@link WebElement} to see if the {@link WebElement#getText()} contains any of the configured
     * {@link ConfigurationProperties#ipAddresses()}.
     *
     * @param element the {@link WebElement} to check
     * @return {@code true} if the {@link WebElement#getText()} contains any of the {@link ConfigurationProperties#ipAddresses()}
     */
    protected boolean doesElementContainIpAddress(final WebElement element) {
        return doesElementContainAnyProvideString(element, CONFIG.ipAddresses());
    }

    private static boolean doesElementContainAnyProvideString(final WebElement element, final Collection<String> stringsToFind) {
        for (final String stringToFind : stringsToFind) {
            if (element.getText().contains(stringToFind)) {
                return true;
            }
        }
        return false;
    }
}
