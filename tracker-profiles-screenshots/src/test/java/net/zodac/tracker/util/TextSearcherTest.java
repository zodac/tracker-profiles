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

package net.zodac.tracker.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Unit tests for {@link TextSearcher}.
 */
class TextSearcherTest {

    @ParameterizedTest
    @ValueSource(strings = {
        "user@example.com",
        "test.user+tag@sub.domain.com",
        "Contact me at admin123@test.co.uk",
        "prefix user@test.com suffix"
    })
    void testValidEmailPresentShouldReturnTrue(final String input) {
        assertThat(TextSearcher.hasEmailAddress(input))
            .isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "",
        "plain text only",
        "user@",
        "@example.com",
        "user@example",
        "192.168.0.1" // NOPMD: AvoidUsingHardCodedIP - intentional test data
    })
    void testNoEmailPresentShouldReturnFalse(final String input) {
        assertThat(TextSearcher.hasEmailAddress(input))
            .isFalse();
    }

    @Test
    void testAnyInputContainsEmailShouldReturnTrue() {
        assertThat(TextSearcher.hasEmailAddress(
            "no email here",
            "another string",
            "contact: user@test.com"
        ))
            .isTrue();
    }

    @Test
    void testNoInputContainsEmailShouldReturnFalse() {
        assertThat(TextSearcher.hasEmailAddress(
            "foo",
            "bar",
            "baz"
        ))
            .isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "192.168.1.1", // NOPMD: AvoidUsingHardCodedIP - intentional test data
        "Server IP: 10.0.0.5",
        "255.255.255.255", // NOPMD: AvoidUsingHardCodedIP - intentional test data
        "0.0.0.0" // NOPMD: AvoidUsingHardCodedIP - intentional test data
    })
    void testValidIpv4PresentShouldReturnTrue(final String input) {
        assertThat(TextSearcher.hasIpAddress(input))
            .isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "192.168.x.x",
        "Connection from 10.0.x.x detected"
    })
    void testMaskedIpv4PresentShouldReturnTrue(final String input) {
        assertThat(TextSearcher.hasIpAddress(input))
            .isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "2001:0db8:85a3:0000:0000:8a2e:0370:7334", // NOPMD: AvoidUsingHardCodedIP - intentional test data
        "fe80:0000:0000:0000:0202:b3ff:fe1e:8329", // NOPMD: AvoidUsingHardCodedIP - intentional test data
        "Last activity 5 days ago from 2404:4120:3101:6f00:632:1acf:f8ed:1569."
    })
    void testValidIpv6PresentShouldReturnTrue(final String input) {
        assertThat(TextSearcher.hasIpAddress(input))
            .isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "2404:1111:506f:",
        "Connection from 2001:0db8:85a3: detected"
    })
    void testPartialIpv6PresentShouldReturnTrue(final String input) {
        assertThat(TextSearcher.hasIpAddress(input))
            .isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "",
        "plain text only",
        "999.999.999.999",
        "192.168.1",
        "12:59:05",
        "Logged at 08:30:00 UTC",
        "2001:db8::1" // NOPMD: AvoidUsingHardCodedIP - intentional test data; compressed IPv6 with short octets does not match the strict pattern
    })
    void testNoValidIpPresentShouldReturnFalse(final String input) {
        assertThat(TextSearcher.hasIpAddress(input))
            .isFalse();
    }

    @Test
    void testAnyInputContainsIpShouldReturnTrue() {
        assertThat(TextSearcher.hasIpAddress(
            "no ip here",
            "another string",
            "connect to 172.16.0.1 immediately"
        ))
            .isTrue();
    }

    @Test
    void testNoInputContainsIpShouldReturnFalse() {
        assertThat(TextSearcher.hasIpAddress(
            "foo",
            "bar",
            "baz"
        ))
            .isFalse();
    }
}
