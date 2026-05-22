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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;
import net.zodac.tracker.framework.annotation.TrackerHandler;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link PackageScanner}.
 */
class PackageScannerTest {

    private static final String SCAN_PACKAGE = "net.zodac.tracker.framework.annotation";

    @Test
    void givenKnownPackage_whenScanAcceptingAll_thenContainsExpectedClasses() {
        final Set<Class<?>> classes = PackageScanner.scan(SCAN_PACKAGE, _ -> true);
        assertThat(classes)
            .extracting(Class::getSimpleName)
            .contains("CommonTrackerHandler", "TrackerHandler", "TrackerHandlers");
    }

    @Test
    void givenFilterMatchingOneClass_whenScan_thenOnlyMatchingClassReturned() {
        final Set<Class<?>> classes = PackageScanner.scan(SCAN_PACKAGE, clazz -> clazz == TrackerHandler.class);
        assertThat(classes)
            .containsExactly(TrackerHandler.class);
    }

    @Test
    void givenFilterRejectingAll_whenScan_thenEmptySetReturned() {
        final Set<Class<?>> classes = PackageScanner.scan(SCAN_PACKAGE, _ -> false);
        assertThat(classes)
            .isEmpty();
    }

    @Test
    void givenScanResults_whenInspected_thenClassesAreSortedByFullyQualifiedName() {
        final Set<Class<?>> classes = PackageScanner.scan(SCAN_PACKAGE, _ -> true);

        assertThat(classes)
            .extracting(Class::getName)
            .isSorted();
    }

    @Test
    void givenUnknownPackage_whenScan_thenIllegalStateExceptionThrown() {
        assertThatThrownBy(() -> PackageScanner.scan("net.zodac.tracker.nonexistent.package", _ -> true))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("net.zodac.tracker.nonexistent.package");
    }
}
