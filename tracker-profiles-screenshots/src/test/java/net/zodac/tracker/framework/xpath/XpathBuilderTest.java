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

package net.zodac.tracker.framework.xpath;

import static net.zodac.tracker.framework.xpath.HtmlElement.a;
import static net.zodac.tracker.framework.xpath.HtmlElement.div;
import static net.zodac.tracker.framework.xpath.HtmlElement.img;
import static net.zodac.tracker.framework.xpath.HtmlElement.input;
import static net.zodac.tracker.framework.xpath.HtmlElement.li;
import static net.zodac.tracker.framework.xpath.HtmlElement.span;
import static net.zodac.tracker.framework.xpath.HtmlElement.table;
import static net.zodac.tracker.framework.xpath.HtmlElement.tr;
import static net.zodac.tracker.framework.xpath.HtmlElement.ul;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.atFirstIndex;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.atIndex;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.atLastIndex;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.containsAttribute;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.containsAttributeFunction;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.containsHref;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.containsSrc;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.withAttribute;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.withAttributeFunction;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.withClass;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.withExactText;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.withHref;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.withId;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.withName;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.withNumberOfChildrenOfType;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.withText;
import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.withType;
import static net.zodac.tracker.framework.xpath.XpathAxis.followingSibling;
import static net.zodac.tracker.framework.xpath.XpathAxis.parent;
import static net.zodac.tracker.framework.xpath.XpathAxis.precedingSibling;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.openqa.selenium.By;

/**
 * Unit tests for {@link XpathBuilder}.
 */
class XpathBuilderTest {

    @Test
    void testBuildShouldReturnByXpathUsingCurrentQuery() {
        final By by = XpathBuilder
            .from(div, withId("test"))
            .build();

        assertThat(by)
            .hasToString("By.xpath: //div[@id='test']");
    }

    @ParameterizedTest
    @CsvSource({
        "div,1,//div[1]",
        "span,2,//span[2]"
    })
    void testFromAtIndexShouldAppendIndexedAnywhereSelector(final HtmlElement element, final int index, final String expected) {
        final String actual = XpathBuilder
            .from(element, atIndex(index))
            .toString();

        assertThat(actual).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
        "div,//div[1]",
        "span,//span[1]"
    })
    void testFromAtFirstIndexShouldAppendIndexedAnywhereSelector(final HtmlElement element, final String expected) {
        final String actual = XpathBuilder
            .from(element, atFirstIndex())
            .toString();

        assertThat(actual).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
        "div,//div//div",
        "span,//div//span",
        "a,//div//a"
    })
    void testDescendantShouldAppendAnywhereSelector(final HtmlElement element, final String expected) {
        final String actual = XpathBuilder
            .from(div)
            .descendant(element)
            .toString();

        assertThat(actual).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
        "div,1,//div//div[1]",
        "span,2,//div//span[2]"
    })
    void testDescendantAtIndexShouldAppendIndexedAnywhereSelector(final HtmlElement element, final int index, final String expected) {
        final String actual = XpathBuilder
            .from(div)
            .descendant(element, atIndex(index))
            .toString();

        assertThat(actual).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
        "div,//root/div",
        "span,//root/span"
    })
    void testChildSelector(final HtmlElement element, final String expected) {
        final String actual = XpathBuilder
            .from(NamedHtmlElement.of("root"))
            .child(element)
            .toString();

        assertThat(actual).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
        "div,1,//root/div[1]",
        "span,2,//root/span[2]"
    })
    void testChildAtIndexSelector(final HtmlElement element, final int index, final String expected) {
        final String actual = XpathBuilder
            .from(NamedHtmlElement.of("root"))
            .child(element, atIndex(index))
            .toString();

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void testNavigateParentShouldAppendAncestorSelector() {
        final String actual = XpathBuilder
            .from(input)
            .navigateTo(parent(div))
            .toString();

        assertThat(actual)
            .isEqualTo("//input/ancestor::div[1]");
    }

    @Test
    void testNavigateFollowingSiblingShouldAppendFollowingSiblingSelector() {
        final String actual = XpathBuilder
            .from(div, withId("header"))
            .navigateTo(followingSibling(div, withClass("content")))
            .toString();

        assertThat(actual)
            .isEqualTo("//div[@id='header']/following-sibling::div[contains(@class, 'content')]");
    }

    @Test
    void testNavigatePrecedingSiblingShouldAppendPrecedingSiblingSelector() {
        final String actual = XpathBuilder
            .from(div, withClass("footer"))
            .navigateTo(precedingSibling(div, atIndex(1)))
            .toString();

        assertThat(actual)
            .isEqualTo("//div[contains(@class, 'footer')]/preceding-sibling::div[1]");
    }

    @Test
    void testWithTextShouldAppendContainsNormalizeSpacePredicate() {
        final String actual = XpathBuilder
            .from(span, withText("hello world"))
            .toString();

        assertThat(actual)
            .isEqualTo("//span[contains(normalize-space(), 'hello world')]");
    }

    @Test
    void testWithNameShouldAppendNameSelector() {
        final String actual = XpathBuilder
            .from(span, withName("my-name"))
            .toString();

        assertThat(actual)
            .isEqualTo("//span[@name='my-name']");
    }

    @Test
    void testWithTypeShouldAppendNameSelector() {
        final String actual = XpathBuilder
            .from(span, withType("type-value"))
            .toString();

        assertThat(actual)
            .isEqualTo("//span[@type='type-value']");
    }

    @Test
    void testWithIdShouldAppendIdPredicate() {
        final String actual = XpathBuilder
            .from(div, withId("main"))
            .toString();

        assertThat(actual)
            .isEqualTo("//div[@id='main']");
    }

    @Test
    void testWithClassShouldAppendContainsClassPredicate() {
        final String actual = XpathBuilder
            .from(div, withClass("active"))
            .toString();

        assertThat(actual)
            .isEqualTo("//div[contains(@class, 'active')]");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "data-test|value|//div[contains(@data-test, 'value')]",
        "aria-label|label|//div[contains(@aria-label, 'label')]"
    })
    void testContainsAttributeShouldAppendPredicate(final String attributeName, final String attributeValue, final String expected) {
        final String actual = XpathBuilder
            .from(div, containsAttribute(attributeName, attributeValue))
            .toString();

        assertThat(actual).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "data-test()|value|//div[contains(data-test(), 'value')]",
        "aria-label()|label|//div[contains(aria-label(), 'label')]"
    })
    void testContainsAttributeFunctionShouldAppendPredicate(final String attributeName, final String attributeValue, final String expected) {
        final String actual = XpathBuilder
            .from(div, containsAttributeFunction(attributeName, attributeValue))
            .toString();

        assertThat(actual).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
        "data-test,value,//div[@data-test='value']",
        "aria-label,label,//div[@aria-label='label']"
    })
    void testWithAttributeShouldAppendExactAttributePredicate(final String attributeName, final String attributeValue, final String expected) {
        final String actual = XpathBuilder
            .from(div, withAttribute(attributeName, attributeValue))
            .toString();

        assertThat(actual).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
        "data-test(),value,//div[data-test()='value']",
        "aria-label(),label,//div[aria-label()='label']"
    })
    void testWithAttributeFunctionShouldAppendExactAttributePredicate(final String attributeName, final String attributeValue,
        final String expected) {
        final String actual = XpathBuilder
            .from(div, withAttributeFunction(attributeName, attributeValue))
            .toString();

        assertThat(actual).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 5, 10})
    void testAtIndexShouldAppendIndexPredicate(final int index) {
        final String actual = XpathBuilder
            .from(li, atIndex(index))
            .toString();

        assertThat(actual)
            .isEqualTo("//li[" + index + "]");
    }

    @Test
    void testAtLastIndexShouldAppendLastIndexPredicate() {
        final String actual = XpathBuilder
            .from(li, atLastIndex())
            .toString();

        assertThat(actual)
            .isEqualTo("//li[last()]");
    }

    @Test
    void testWithExactTextShouldAppendNormalizeSpaceEqualsPredicate() {
        final String actual = XpathBuilder
            .from(span, withExactText("hello world"))
            .toString();

        assertThat(actual)
            .isEqualTo("//span[normalize-space()='hello world']");
    }

    @Test
    void testWithHrefShouldAppendHrefPredicate() {
        final String actual = XpathBuilder
            .from(a, withHref("/profile"))
            .toString();

        assertThat(actual)
            .isEqualTo("//a[@href='/profile']");
    }

    @Test
    void testContainsHrefShouldAppendContainsHrefPredicate() {
        final String actual = XpathBuilder
            .from(a, containsHref("/profile"))
            .toString();

        assertThat(actual)
            .isEqualTo("//a[contains(@href, '/profile')]");
    }

    @ParameterizedTest
    @CsvSource({
        "div,1,//div[count(div)=1]",
        "div,3,//div[count(div)=3]",
        "span,2,//div[count(span)=2]"
    })
    void testWithNumberOfChildrenOfTypeShouldAppendCountPredicate(final HtmlElement childElement, final int count, final String expected) {
        final String actual = XpathBuilder
            .from(div, withNumberOfChildrenOfType(childElement, count))
            .toString();

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void testContainsSrcShouldAppendContainsSrcPredicate() {
        final String actual = XpathBuilder
            .from(img, containsSrc("images/flag"))
            .toString();

        assertThat(actual)
            .isEqualTo("//img[contains(@src, 'images/flag')]");
    }

    @Test
    void testNamedHtmlElementAnyShouldProduceWildcardSelector() {
        final String actual = XpathBuilder
            .from(NamedHtmlElement.any())
            .toString();

        assertThat(actual)
            .isEqualTo("//*");
    }

    @Test
    void testValueWithApostropheShouldBeEscaped() {
        final String actual = XpathBuilder
            .from(div, withClass("user's-profile"))
            .toString();

        assertThat(actual)
            .isEqualTo("//div[contains(@class, 'user\\'s-profile')]");
    }

    @Test
    void testMultiplePredicatesStackedOnSingleStepShouldAllBeApplied() {
        final String actual = XpathBuilder
            .from(input, withType("text"), withClass("login"), atIndex(2))
            .toString();

        assertThat(actual)
            .isEqualTo("//input[@type='text'][contains(@class, 'login')][2]");
    }

    @Test
    void testComplexChainingShouldBeHandledCorrectly() {
        final String actual = XpathBuilder
            .from(div, withId("container"))
            .descendant(table)
            .descendant(tr, withAttribute("title", "stats"), atIndex(3))
            .child(ul)
            .child(li, withClass("active"), atIndex(2))
            .toString();

        assertThat(actual)
            .isEqualTo("//div[@id='container']//table//tr[@title='stats'][3]/ul/li[contains(@class, 'active')][2]");
    }
}
