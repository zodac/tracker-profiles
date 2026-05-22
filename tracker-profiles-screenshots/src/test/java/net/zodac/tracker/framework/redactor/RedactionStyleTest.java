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

package net.zodac.tracker.framework.redactor;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link RedactionStyle}.
 */
class RedactionStyleTest {

    @Test
    void givenBlurFactory_whenCreated_thenBlurModeAndEmptyBoxFields() {
        final RedactionStyle style = RedactionStyle.blur();

        assertThat(style.mode()).isEqualTo("blur");
        assertThat(style.bgColor()).isEmpty();
        assertThat(style.textColor()).isEmpty();
        assertThat(style.label()).isEmpty();
        assertThat(style.blurDef()).isEqualTo("blur(0.5em)");
    }

    @Test
    void givenBoxFactory_whenCreated_thenBoxModeAndEmptyBlurField() {
        final RedactionStyle style = RedactionStyle.box("#000", "#fff", "EMAIL");

        assertThat(style.mode()).isEqualTo("box");
        assertThat(style.bgColor()).isEqualTo("#000");
        assertThat(style.textColor()).isEqualTo("#fff");
        assertThat(style.label()).isEqualTo("EMAIL");
        assertThat(style.blurDef()).isEmpty();
    }
}
