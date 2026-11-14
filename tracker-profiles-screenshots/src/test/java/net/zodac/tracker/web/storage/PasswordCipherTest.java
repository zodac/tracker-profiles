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

package net.zodac.tracker.web.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link PasswordCipher}.
 */
class PasswordCipherTest {

    private static final String SECRET_KEY = "test-secret-key-value";

    @Test
    void givenSameSecretAndSalt_whenCreatedAgain_thenPasswordIsDecrypted() {
        final byte[] salt = PasswordCipher.newSalt();
        final String encrypted = PasswordCipher.fromSecretKey(SECRET_KEY, salt).encrypt("Tracker", "password");

        assertThat(PasswordCipher.fromSecretKey(SECRET_KEY, salt).decrypt("Tracker", encrypted))
            .isEqualTo("password");
    }

    @Test
    void givenDifferentSecret_whenDecrypted_thenExceptionIsThrown() {
        final byte[] salt = PasswordCipher.newSalt();
        final String encrypted = PasswordCipher.fromSecretKey(SECRET_KEY, salt).encrypt("Tracker", "password");
        final PasswordCipher otherCipher = PasswordCipher.fromSecretKey("another-secret-key-value", salt);

        assertThatThrownBy(() -> otherCipher.decrypt("Tracker", encrypted))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("different key");
    }

    @Test
    void givenPassword_whenEncryptedTwice_thenValuesDifferAndBothDecrypt() {
        final PasswordCipher passwordCipher = PasswordCipher.fromSecretKey(SECRET_KEY, PasswordCipher.newSalt());

        final String first = passwordCipher.encrypt("Tracker", "password");
        final String second = passwordCipher.encrypt("Tracker", "password");

        assertThat(first)
            .isNotEqualTo(second)
            .startsWith(PasswordCipher.ENCRYPTED_PREFIX);
        assertThat(passwordCipher.decrypt("Tracker", first))
            .isEqualTo("password");
        assertThat(passwordCipher.decrypt("TRACKER", second))
            .isEqualTo("password");
    }

    @Test
    void givenEmptyPassword_whenEncrypted_thenValueIsEmpty() {
        final PasswordCipher passwordCipher = PasswordCipher.fromSecretKey(SECRET_KEY, PasswordCipher.newSalt());

        assertThat(passwordCipher.encrypt("Tracker", ""))
            .isEmpty();
        assertThat(passwordCipher.decrypt("Tracker", ""))
            .isEmpty();
    }

    @Test
    void givenPasswordForAnotherTracker_whenDecrypted_thenExceptionIsThrown() {
        final PasswordCipher passwordCipher = PasswordCipher.fromSecretKey(SECRET_KEY, PasswordCipher.newSalt());
        final String encrypted = passwordCipher.encrypt("Tracker", "password");

        assertThatThrownBy(() -> passwordCipher.decrypt("OtherTracker", encrypted))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("OtherTracker");
    }

    @Test
    void givenShortSecret_whenCreated_thenExceptionIsThrown() {
        final byte[] salt = PasswordCipher.newSalt();

        assertThatThrownBy(() -> PasswordCipher.fromSecretKey("short", salt))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("SECRET_KEY");
    }
}
