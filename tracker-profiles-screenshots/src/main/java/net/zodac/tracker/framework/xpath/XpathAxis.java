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

/**
 * Utility {@link XpathNavigation} functions to traverse the DOM using XPath axes.
 *
 * <p>
 * In contrast to {@link XpathAttributePredicate}, which filters elements within a step using {@code []} predicate syntax, methods in this class
 * produce axis-based navigation steps that move through the DOM in directions such as ancestor, following sibling, or preceding sibling.
 *
 * @see XpathBuilder
 */
public final class XpathAxis {

    private XpathAxis() {

    }

    /**
     * Update the XPATH query to find the nearest parent element matching the given {@link Element}.
     *
     * <p>
     * Adds:
     *
     * <pre>
     *      //existing_query<u>/ancestor::element[1]</u>
     * </pre>
     *
     * @param element the name of the HTML element
     * @return an {@link XpathNavigation} for the ancestor axis
     */
    public static XpathNavigation parent(final Element element) {
        return xpath -> xpath.append("/ancestor::").append(element.tagName()).append("[1]");
    }

    /**
     * Update the XPATH query to find a following sibling of the current element, with the provided {@link XpathPredicate}s.
     *
     * <p>
     * Adds:
     *
     * <pre>
     *      //existing_query<u>/following-sibling::element[@attribute-name='attribute-value']</u>
     * </pre>
     *
     * @param element    the name of the HTML element
     * @param predicates any {@link XpathPredicate}s to filter the sibling
     * @return an {@link XpathNavigation} for the following-sibling axis
     */
    public static XpathNavigation followingSibling(final Element element, final XpathPredicate... predicates) {
        return xpath -> {
            xpath.append("/following-sibling::")
                .append(element.tagName());

            for (final XpathPredicate predicate : predicates) {
                predicate.apply(xpath);
            }
        };
    }

    /**
     * Update the XPATH query to find a preceding sibling of the current element, with the provided {@link XpathPredicate}s.
     *
     * <p>
     * Adds:
     *
     * <pre>
     *      //existing_query<u>/preceding-sibling::element[@attribute-name='attribute-value']</u>
     * </pre>
     *
     * @param element    the name of the HTML element
     * @param predicates any {@link XpathPredicate}s to filter the sibling
     * @return an {@link XpathNavigation} for the preceding-sibling axis
     */
    public static XpathNavigation precedingSibling(final Element element, final XpathPredicate... predicates) {
        return xpath -> {
            xpath.append("/preceding-sibling::")
                .append(element.tagName());

            for (final XpathPredicate predicate : predicates) {
                predicate.apply(xpath);
            }
        };
    }
}
