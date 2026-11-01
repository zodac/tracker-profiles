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

import org.openqa.selenium.By;

/**
 * Each {@link XpathStep} incrementally builds an XPath query by appending to a {@link XpathBuilder} instance, including XPath navigation steps, such
 * as the following.
 * <ul>
 *     <li>descendant</li>
 *     <li>direct child</li>
 *     <li>parent</li>
 * </ul>
 *
 * <p>
 * Each of these can also be additionally constrained by {@link XpathPredicate}s. Each method mutates the current query and returns the same instance
 * to allow fluent chaining.
 *
 * @see XpathBuilder
 */
public final class XpathStep {

    private final StringBuilder xpath = new StringBuilder();  // NOPMD: AvoidStringBufferField - Fine here

    /**
     * Default constructor.
     */
    XpathStep() {

    }

    /**
     * Begin the XPATH query to find an element anywhere in the DOM under the current position, with the provided {@link XpathPredicate}s.
     *
     * <p>
     * Should only be used by {@link XpathBuilder} to begin the query construction.
     *
     * <p>
     * Adds:
     *
     * <pre>
     *      <u>//element[@attribute-name='attribute-value']</u>
     * </pre>
     *
     * @param element    the name of the HTML element
     * @param predicates any remaining {@link XpathPredicate}s
     * @return this {@link XpathBuilder}
     */
    XpathStep from(final Element element, final XpathPredicate... predicates) {
        xpath.append("//").append(element.tagName());
        applyPredicates(predicates);
        return this;
    }

    /**
     * Update the XPATH query to find the first element that is a direct child element of the current position, with the specified index.
     *
     * <p>
     * Adds:
     *
     * <pre>
     *      //existing_query<u>/element[@attribute-name='attribute-value']</u>
     * </pre>
     *
     * @param element    the name of the HTML element
     * @param predicates any remaining {@link XpathPredicate}s
     * @return this {@link XpathBuilder}
     */
    public XpathStep child(final Element element, final XpathPredicate... predicates) {
        xpath.append('/').append(element.tagName());
        applyPredicates(predicates);
        return this;
    }

    /**
     * Update the XPATH query to find an element anywhere in the DOM under the current position, with the provided {@link XpathPredicate}s.
     *
     * <p>
     * Adds:
     *
     * <pre>
     *      //existing_query<u>//element[@attribute-name='attribute-value']</u>
     * </pre>
     *
     * @param element    the name of the HTML element
     * @param predicates any remaining {@link XpathPredicate}s
     * @return this {@link XpathBuilder}
     */
    public XpathStep descendant(final Element element, final XpathPredicate... predicates) {
        return from(element, predicates);
    }

    /**
     * Update the XPATH query by applying the given {@link XpathNavigation} axis step to the current position.
     *
     * <p>
     * Unlike {@link #child(Element, XpathPredicate...)} and {@link #descendant(Element, XpathPredicate...)}, which select elements by structural
     * position, this method allows navigating the DOM using XPath axes such as {@code ancestor::}, {@code following-sibling::}, and
     * {@code preceding-sibling::}.
     *
     * <p>
     * Adds:
     *
     * <pre>
     *      //existing_query<u>/axis::element[predicates]</u>
     * </pre>
     *
     * @param navigation the {@link XpathNavigation} axis step to apply
     * @return this {@link XpathStep}
     * @see XpathAxis
     */
    public XpathStep navigateTo(final XpathNavigation navigation) {
        navigation.apply(xpath);
        return this;
    }

    /**
     * Creates a {@link By#xpath(String)} selector for the query.
     *
     * @return the XPath query
     */
    public By build() {
        return By.xpath(xpath.toString());
    }

    @Override
    public String toString() {
        return xpath.toString();
    }

    private void applyPredicates(final XpathPredicate... otherPredicates) {
        for (final XpathPredicate predicate : otherPredicates) {
            predicate.apply(xpath);
        }
    }
}
