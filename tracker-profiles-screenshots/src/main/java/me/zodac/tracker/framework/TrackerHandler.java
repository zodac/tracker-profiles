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
     * The user's email address, defined by the environment variable {@code EMAIL_ADDRESS}.
     */
    protected static final String EMAIL_ADDRESS = System.getProperty("EMAIL_ADDRESS", System.getenv("EMAIL_ADDRESS"));

    /**
     * The user's host IP address, defined by the environment variable {@code HOST_IP_ADDRESS}.
     */
    protected static final String HOST_IP_ADDRESS = System.getProperty("HOST_IP_ADDRESS", System.getenv("HOST_IP_ADDRESS"));

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
     * @param trackerInfo the {@link TrackerInfo} containing the login page URL
     */
    public abstract void openLoginPage(TrackerInfo trackerInfo);

    /**
     * Enters the user's credential and logs in to the tracker. Ideally, this method will wait until the redirect page after login has fully loaded.
     *
     * @param trackerInfo the {@link TrackerInfo} containing the login credentials
     */
    public abstract void login(TrackerInfo trackerInfo);

    /**
     * Once logged in, navigates to the user's profile page on the tracker.
     *
     * @param trackerInfo the {@link TrackerInfo} containing the user's profile URL
     */
    public abstract void openProfilePage(TrackerInfo trackerInfo);

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
}
