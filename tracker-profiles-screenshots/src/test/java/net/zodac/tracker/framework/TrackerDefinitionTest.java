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

package net.zodac.tracker.framework;

import static org.assertj.core.api.Assertions.assertThat;

import net.zodac.tracker.framework.annotation.TrackerHandler;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link TrackerDefinition}.
 */
class TrackerDefinitionTest {

    @Test
    void givenAnnotationWithExplicitType_whenFromAnnotation_thenAllFieldsCopied() {
        final TrackerHandler annotation = SingleUrlSample.class.getAnnotation(TrackerHandler.class);

        final TrackerDefinition definition = TrackerDefinition.fromAnnotation(annotation);
        assertThat(definition.name())
            .isEqualTo("SampleSingleUrl");
        assertThat(definition.type())
            .isEqualTo(TrackerType.MANUAL);
        assertThat(definition.urls())
            .containsExactly("https://example.com/");
    }

    @Test
    void givenAnnotationWithMultipleUrls_whenFromAnnotation_thenAllUrlsPreserved() {
        final TrackerHandler annotation = MultiUrlSample.class.getAnnotation(TrackerHandler.class);

        final TrackerDefinition definition = TrackerDefinition.fromAnnotation(annotation);
        assertThat(definition.name())
            .isEqualTo("SampleMultiUrl");
        assertThat(definition.type())
            .isEqualTo(TrackerType.HEADLESS);
        assertThat(definition.urls())
            .containsExactly("https://primary.example.com/", "https://backup.example.com/");
    }

    @TrackerHandler(name = "SampleSingleUrl", type = TrackerType.MANUAL, url = "https://example.com/")
    private static final class SingleUrlSample {

    }

    @TrackerHandler(name = "SampleMultiUrl", url = {"https://primary.example.com/", "https://backup.example.com/"})
    private static final class MultiUrlSample {

    }
}
