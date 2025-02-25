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
import java.util.List;
import net.zodac.tracker.framework.TrackerHandler;
import net.zodac.tracker.util.ScriptExecutor;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;

/**
 * Implementation of {@link AbstractTrackerHandler} for the {@code Empornium} tracker.
 */
@TrackerHandler("Empornium")
public class EmporniumHandler extends AbstractTrackerHandler {

    /**
     * Default constructor.
     *
     * @param driver a {@link ChromeDriver} used to load web pages and perform UI actions
     */
    public EmporniumHandler(final ChromeDriver driver) {
        super(driver);
    }

    @Override
    protected By usernameFieldSelector() {
        return By.xpath("//div[@id='username']//input[@name='username']");
    }

    @Override
    protected By passwordFieldSelector() {
        return By.xpath("//div[@id='password']//input[@name='password']");
    }

    @Override
    public By loginButtonSelector() {
        return By.id("login_button");
    }

    @Override
    protected By profilePageSelector() {
        return By.xpath("//a[@class='username']");
    }

    @Override
    protected Collection<By> getElementsPotentiallyContainingSensitiveInformation() {
        return List.of(
            By.tagName("a")
        );
    }

    @Override
    protected By logoutButtonSelector() {
        // Highlight the nav bar to make the logout button interactable
        final By logoutParentBy = By.id("userinfo_username");
        final WebElement logoutParent = driver.findElement(logoutParentBy);
        ScriptExecutor.moveTo(driver, logoutParent);

        return By.xpath("//li[@id='nav_logout']//a[1]");
    }

    @Override
    protected By postLogoutElementSelector() {
        return By.xpath("//div[@id='maincontent']//a[text()='Login']");
    }
}
