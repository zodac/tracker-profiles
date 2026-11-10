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

package net.zodac.tracker.framework.redaction;

import static org.assertj.core.api.Assertions.assertThat;

import net.zodac.tracker.handler.redaction.Redaction;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link RedactionEntry}.
 */
class RedactionEntryTest {

    @Test
    void givenHandlerImplementingInterface_whenApplies_thenTrueReturned() {
        final RedactionEntry<FakeRedaction> entry = buildEntry();
        assertThat(entry.applies(new FakeHandler(3)))
            .isTrue();
    }

    @Test
    void givenHandlerNotImplementingInterface_whenApplies_thenFalseReturned() {
        final RedactionEntry<FakeRedaction> entry = buildEntry();
        assertThat(entry.applies(new UnrelatedHandler()))
            .isFalse();
    }

    @Test
    void givenHandlerWithEmptySelectors_whenIsEmpty_thenTrueReturned() {
        final RedactionEntry<FakeRedaction> entry = buildEntry();
        assertThat(entry.isEmpty(new FakeHandler(0)))
            .isTrue();
    }

    @Test
    void givenHandlerWithSelectors_whenIsEmpty_thenFalseReturned() {
        final RedactionEntry<FakeRedaction> entry = buildEntry();
        assertThat(entry.isEmpty(new FakeHandler(2)))
            .isFalse();
    }

    @Test
    void givenEntry_whenDescription_thenReturnsConfiguredValue() {
        final RedactionEntry<FakeRedaction> entry = buildEntry();
        assertThat(entry.description())
            .isEqualTo("fake");
    }

    private static RedactionEntry<FakeRedaction> buildEntry() {
        return RedactionEntry.of(
            FakeRedaction.class,
            (_, _, _) -> 0,
            target -> target.selectorsSize() == 0,
            "fake"
        );
    }

    private interface FakeRedaction extends Redaction { // NOPMD: ImplicitFunctionalInterface - test helper, not a lambda target
        int selectorsSize();
    }

    private record FakeHandler(int selectorsSize) implements FakeRedaction {

    }

    private static final class UnrelatedHandler {

    }
}
