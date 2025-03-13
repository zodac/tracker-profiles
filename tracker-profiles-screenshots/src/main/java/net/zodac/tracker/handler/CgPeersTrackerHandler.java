/*
 * BSD Zero Clause License
 *
 * Copyright (c) 2021-2025 zodac.net
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

package net.zodac.tracker.handler;

import java.util.Collection;
import net.zodac.tracker.framework.TrackerHandler;
import net.zodac.tracker.framework.gui.DisplayUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.By;
import org.openqa.selenium.chrome.ChromeDriver;

/**
 * Implementation of {@link AbstractTrackerHandler} for the {@code CGPeers} tracker.
 */
@TrackerHandler(name = "CGPeers", needsManualInput = true, url = {
    "https://cgpeers.to",
    "https://cgpeers.com"
})
public class CgPeersTrackerHandler extends AbstractTrackerHandler {

    private static final Logger LOGGER = LogManager.getLogger();

    /**
     * Default constructor.
     *
     * @param driver      a {@link ChromeDriver} used to load web pages and perform UI actions
     * @param trackerUrls the URLs to the tracker
     */
    public CgPeersTrackerHandler(final ChromeDriver driver, final Collection<String> trackerUrls) {
        super(driver, trackerUrls);
    }

    @Override
    public By loginPageSelector() {
        return By.xpath("//a[text()='Login']");
    }

    @Override
    protected By loginButtonSelector() {
        return By.xpath("//input[@type='submit' and @name='login']");
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * For {@link CgPeersTrackerHandler}, 2FA is enabled, so a passcode is sent to your email address and must be entered here. This must be done
     * within {@link DisplayUtils#INPUT_WAIT_DURATION}.
     *
     * <p>
     * Note that because there are multiple screens for this authentication, none of the elements will be highlighted.
     *
     * <p>
     * Manual user interaction:
     * <ol>
     *     <li>Complete 2FA</li>
     * </ol>
     */
    @Override
    protected void manualCheckAfterLoginClick(final String trackerName) {
        LOGGER.info("\t\t >>> Waiting for user to complete 2FA, for {} seconds", DisplayUtils.INPUT_WAIT_DURATION.getSeconds());
        DisplayUtils.userInputConfirmation(trackerName, "Complete 2FA");
    }

    @Override
    protected By postLoginSelector() {
        return By.id("userinfo");
    }

    @Override
    protected By profilePageSelector() {
        return By.xpath("//li[@id='nav_userinfo']/a[1]");
    }

    @Override
    protected By logoutButtonSelector() {
        return By.xpath("//li[@id='nav_logout']/a[1]");
    }
}
