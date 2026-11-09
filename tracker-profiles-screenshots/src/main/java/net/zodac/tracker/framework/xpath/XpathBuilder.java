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
 * Entry point for building XPath expressions using a fluent, type-safe API.
 *
 * <p>
 * {@link XpathBuilder} provides static factory methods to begin an XPath query from the root of the DOM. From there, the returned {@link XpathStep}
 * can be used to incrementally navigate the document structure using descendant, direct-child, and parent relationships, optionally constrained by
 * {@link XpathPredicate}s.
 *
 * <p>
 * The following examples demonstrate typical usage patterns, from simple element selection to more complex structural navigation.
 *
 * <h2>Example 1: Simple element lookup</h2>
 *
 * <p>
 * {@snippet id = 'example1':
 * final By selector = XpathBuilder
 *     .from(HtmlElement.div)
 *     .build();
 * }
 *
 * <p>
 * Resulting XPath: {@code //div}
 *
 * <h2>Example 2: Direct child with predicates</h2>
 *
 * <p>
 * {@snippet id = 'example2':
 * final By selector = XpathBuilder
 *     .from(HtmlElement.form)
 *     .child(HtmlElement.input, XpathAttributePredicate.withClass("email"), XpathAttributePredicate.withAttribute("title", "user-email"))
 *     .build();
 * }
 *
 * <p>
 * Resulting XPath: {@code //form/input[contains(@class, 'email')][@title='user-email']}
 *
 * <h2>Example 3: Complex navigation with descendants and parent lookup</h2>
 *
 * <p>
 * {@snippet id = 'example3':
 * final By selector = XpathBuilder
 *     .from(NamedHtmlElement.of("root"))
 *     .descendant(HtmlElement.table, XpathAttributePredicate.atFirstIndex())
 *     .descendant(HtmlElement.td, XpathAttributePredicate.withText("Total"))
 *     .navigateTo(XpathAxis.parent(HtmlElement.tr))
 *     .child(HtmlElement.td, XpathAttributePredicate.atIndex(2))
 *   .build();
 * }
 *
 * <p>
 * Resulting XPath: {@code //root//table[1]//td[contains(normalize-space(), 'Total')]/ancestor::tr[1]/td[2]}
 *
 * <p>
 * These examples can be a little cleaner if you statically import the required classes, to something a bit more readable:
 *
 * <p>
 * {@snippet id = 'example4':
 * import static net.zodac.tracker.framework.xpath.HtmlElement.table;
 * import static net.zodac.tracker.framework.xpath.HtmlElement.td;
 * import static net.zodac.tracker.framework.xpath.HtmlElement.tr;
 * import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.atFirstIndex;
 * import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.atIndex;
 * import static net.zodac.tracker.framework.xpath.XpathAttributePredicate.withText;
 * import static net.zodac.tracker.framework.xpath.XpathAxis.parent;
 *
 * final By selector = XpathBuilder
 *     .from(NamedHtmlElement.of("root"))
 *     .descendant(table, atFirstIndex())
 *     .descendant(td, withText("Total"))
 *     .navigateTo(parent(tr))
 *     .child(td, atIndex(2))
 *   .build();
 * }
 *
 * @see XpathStep
 * @see XpathAttributePredicate
 * @see XpathPredicate
 */
public final class XpathBuilder {

    private XpathBuilder() {

    }

    /**
     * Begins the Xpath query to find an element anywhere from the root of the DOM, with any provided {@link XpathPredicate}s.
     *
     * <p>
     * Adds:
     *
     * <pre>
     *      <u>//element[@attribute-name='attribute-value']</u>
     * </pre>
     *
     * @param element    the name of the HTML element
     * @param predicates any {@link XpathPredicate}s
     * @return this {@link XpathBuilder}
     */
    public static XpathStep from(final Element element, final XpathPredicate... predicates) {
        return new XpathStep().from(element, predicates);
    }
}
