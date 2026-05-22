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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Unit tests for {@link NamedHtmlElement}.
 */
class NamedHtmlElementTest {

    @ParameterizedTest
    @CsvSource({
        "section,section",
        "SVG,svg",
        "MyComponent,mycomponent",
        "TR,tr"
    })
    void givenValidTagName_whenOf_thenTagNameLowercased(final String input, final String expected) {
        final NamedHtmlElement element = NamedHtmlElement.of(input);

        assertThat(element.tagName())
            .isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "   ", "\t"})
    void givenBlankTagName_whenOf_thenIllegalArgumentExceptionThrown(final String input) {
        assertThatThrownBy(() -> NamedHtmlElement.of(input))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("blank");
    }

    @Test
    void givenAny_whenInvoked_thenWildcardTagNameReturned() {
        assertThat(NamedHtmlElement.any().tagName())
            .isEqualTo("*");
    }
}
