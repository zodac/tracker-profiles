/*
 * BSD Zero Clause License
 *
 * Copyright (c) 2024-2026 zodac.net
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

import static net.zodac.tracker.framework.xpath.HtmlElement.a;
import static net.zodac.tracker.framework.xpath.HtmlElement.div;
import static net.zodac.tracker.framework.xpath.HtmlElement.input;
import static net.zodac.tracker.framework.xpath.HtmlElement.table;
import static net.zodac.tracker.framework.xpath.HtmlElement.tbody;
import static net.zodac.tracker.framework.xpath.HtmlElement.td;
import static net.zodac.tracker.framework.xpath.HtmlElement.tr;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.atIndex;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.withClass;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.withId;

import java.util.List;
import net.zodac.tracker.framework.TrackerType;
import net.zodac.tracker.framework.annotation.TrackerHandler;
import net.zodac.tracker.framework.xpath.XpathBuilder;
import net.zodac.tracker.handler.definition.HasFixedHeader;
import net.zodac.tracker.handler.definition.NeedsExplicitTranslation;
import net.zodac.tracker.util.BrowserInteractionHelper;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.remote.RemoteWebDriver;

/**
 * Extension of the {@link TsSpecialEditionHandler} for the {@code LetSeed} tracker.
 */
@TrackerHandler(name = "LetSeed", type = TrackerType.MANUAL, url = "https://letseed.org/")
public class LetSeed extends TsSpecialEditionHandler implements HasFixedHeader, NeedsExplicitTranslation {

    @Override
    protected By loginPageSelector() {
        return XpathBuilder
            .from(div, withClass("memberArea"))
            .child(a, atIndex(1))
            .build();
    }

    @Override
    protected By usernameFieldSelector() {
        return By.id("usernameloginphp");
    }

    @Override
    protected By passwordFieldSelector() {
        return XpathBuilder
            .from(input, withClass("inputPassword"))
            .build();
    }

    @Override
    protected By loginButtonSelector() {
        return XpathBuilder
            .from(div, withId("main"))
            .descendant(table, atIndex(1))
            .child(tbody, atIndex(1))
            .child(tr, atIndex(6))
            .child(td, atIndex(2))
            .child(input, atIndex(1))
            .build();
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * For {@link LetSeed}, prior to clicking the login button with a successful username/password there is another field where a
     * Captcha needs to be entered.
     *
     * <p>
     * Manual user interaction:
     * <ol>
     *     <li>Enter correct captcha value</li>
     * </ol>
     */
    @Override
    protected void preLoginClickAction() {
        LOGGER.info("\t\t >>> Waiting for user to enter captcha");

        final By captchaSelector = By.id("regimage");
        final WebElement captchaElement = driver().findElement(captchaSelector);
        browserInteractionHelper().highlightElement(captchaElement);
        displayUtils().confirm(trackerDefinition().name(), "Solve the captcha");
    }

    @Override
    protected By postLoginSelector() {
        return By.id("quickprofileview");
    }

    @Override
    public void translatePageToEnglish() {
        final By englishLanguageOptionSelector = XpathBuilder
            .from(div, withClass("flags"))
            .child(a, atIndex(1))
            .build();
        final WebElement englishLanguageOption = driver().findElement(englishLanguageOptionSelector);
        clickButton(englishLanguageOption);
    }

    @Override
    public void unfixHeaders(final RemoteWebDriver driver, final List<By> headerSelectors) {
        final BrowserInteractionHelper browserInteractionHelper = browserInteractionHelper();
        for (final By headerSelector : headerSelectors) {
            final WebElement headerElement = driver.findElement(headerSelector);

            LOGGER.debug("\t\t\t- Updating header with 'fixedMenu' class, to set up static state");
            browserInteractionHelper.addClass(headerElement, "fixedMenu");

            browserInteractionHelper.makeUnfixed(headerElement);
        }
    }

    @Override
    public List<By> headerSelectors() {
        return List.of(XpathBuilder
            .from(div, withId("menu"))
            .build());
    }

    @Override
    protected By profileLinkSelector() {
        openUserDropdownMenu();
        return XpathBuilder
            .from(div, withClass("qactions"))
            .descendant(a, atIndex(1))
            .build();
    }

    @Override
    protected By logoutButtonSelector() {
        openUserDropdownMenu();
        return XpathBuilder
            .from(div, withClass("qactions"))
            .child(a, atIndex(2))
            .build();
    }

    private void openUserDropdownMenu() {
        LOGGER.debug("\t\t- Clicking user dropdown menu to make profile/logout button interactable");
        final By profileParentSelector = By.id("quickprofileview");
        final WebElement profileParent = driver().findElement(profileParentSelector);
        clickButton(profileParent);
    }
}
