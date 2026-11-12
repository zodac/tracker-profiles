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

import java.util.Locale;

/**
 * Represents a curated set of well-known HTML elements that can be used when constructing XPath expressions.
 *
 * <p>
 * Using {@code HtmlElement} provides compile-time safety and IDE auto-completion for common HTML tags, avoiding the need to rely on raw
 * {@link String} literals.
 *
 * <p>
 * Each enum constant maps directly to its corresponding lowercase HTML tag name when rendered into an XPath expression.
 *
 * <p>
 * For elements that are not part of this predefined set (e.g. SVG tags, web components, or uncommon elements), use {@link NamedHtmlElement} instead.
 *
 * @see Element
 * @see NamedHtmlElement
 */
@SuppressWarnings("PMD") // Suppressing PMD since the enum values are not uppercase, but lowercase makes more sense for these values
public enum HtmlElement implements Element {

    /**
     * Anchor {@link Element}: {@code <a>}.
     */
    a,

    /**
     * Body {@link Element}: {@code <body>}.
     */
    body,

    /**
     * Button {@link Element}: {@code <button>}.
     */
    button,

    /**
     * Code {@link Element}: {@code <code>}.
     */
    code,

    /**
     * Generic container {@link Element}: {@code <div>}.
     */
    div,

    /**
     * Footer {@link Element}: {@code <footer>}.
     */
    footer,

    /**
     * Form {@link Element}: {@code <form>}.
     */
    form,

    /**
     * Head {@link Element}: {@code <head>}.
     */
    head,

    /**
     * Header {@link Element}: {@code <header>}.
     */
    header,

    /**
     * Image {@link Element}: {@code <img>}.
     */
    img,

    /**
     * Input control {@link Element}: {@code <input>}.
     */
    input,

    /**
     * Label item {@link Element}: {@code <label>}.
     */
    label,

    /**
     * List item {@link Element}: {@code <li>}.
     */
    li,

    /**
     * Main {@link Element}: {@code <main>}.
     */
    main,

    /**
     * Navigation {@link Element}: {@code <nav>}.
     */
    nav,

    /**
     * Ordered list {@link Element}: {@code <ol>}.
     */
    ol,

    /**
     * Paragraph {@link Element}: {@code <p>}.
     */
    p,

    /**
     * Inline container {@link Element}: {@code <span>}.
     */
    span,

    /**
     * Table {@link Element}: {@code <table>}.
     */
    table,

    /**
     * Table body {@link Element}: {@code <tbody>}.
     */
    tbody,

    /**
     * Table cell {@link Element}: {@code <td>}.
     */
    td,

    /**
     * Table header {@link Element}: {@code <th>}.
     */
    th,

    /**
     * Table row {@link Element}: {@code <tr>}.
     */
    tr,

    /**
     * Unordered list {@link Element}: {@code <ul>}.
     */
    ul;

    @Override
    public String tagName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
