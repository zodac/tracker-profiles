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

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Locale;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Encrypts and decrypts the tracker passwords stored in the {@link ApplicationDatabase}, using AES-256-GCM with a key derived from the
 * {@code SECRET_KEY} environment variable. The password cannot be hashed, since it must be sent to the tracker to log in.
 *
 * <p>
 * Each password is encrypted with a random IV, and the tracker name as associated data, so an encrypted password cannot be copied to another
 * tracker. An encrypted value is stored as {@value #ENCRYPTED_PREFIX} followed by the Base64-encoded IV and ciphertext.
 */
public final class PasswordCipher {

    /**
     * The prefix of an encrypted password, used to tell it apart from a password stored in plain text by an earlier version.
     */
    static final String ENCRYPTED_PREFIX = "aes-gcm:";

    /**
     * The minimum length of the secret.
     */
    static final int MINIMUM_SECRET_KEY_LENGTH = 16;

    private static final String KEY_DERIVATION_ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int KEY_DERIVATION_ITERATIONS = 600_000;
    private static final int SALT_LENGTH_BYTES = 16;
    private static final String KEY_ALGORITHM = "AES";
    private static final String CIPHER_ALGORITHM = "AES/GCM/NoPadding";
    private static final int KEY_LENGTH_BYTES = 32;
    private static final int IV_LENGTH_BYTES = 12;
    private static final int TAG_LENGTH_BITS = 128;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final SecretKey secretKey;

    private PasswordCipher(final SecretKey secretKey) {
        this.secretKey = secretKey;
    }

    /**
     * Creates a {@link PasswordCipher} with a key derived from the secret (the {@code SECRET_KEY} environment variable) and the salt of the
     * database, using PBKDF2.
     *
     * @param secretKey the secret
     * @param salt      the random salt stored in the database
     * @return the {@link PasswordCipher}
     * @throws IllegalArgumentException thrown if the secret is shorter than {@value #MINIMUM_SECRET_KEY_LENGTH} characters
     */
    public static PasswordCipher fromSecretKey(final String secretKey, final byte[] salt) {
        if (secretKey.length() < MINIMUM_SECRET_KEY_LENGTH) {
            throw new IllegalArgumentException(
                "[SECRET_KEY] Must be set to at least %d characters, to encrypt the stored passwords (e.g. the output of 'openssl rand -base64 32')"
                    .formatted(MINIMUM_SECRET_KEY_LENGTH));
        }

        final PBEKeySpec keySpec = new PBEKeySpec(secretKey.toCharArray(), salt, KEY_DERIVATION_ITERATIONS, KEY_LENGTH_BYTES * Byte.SIZE);
        try {
            final byte[] key = SecretKeyFactory.getInstance(KEY_DERIVATION_ALGORITHM).generateSecret(keySpec).getEncoded();
            return new PasswordCipher(new SecretKeySpec(key, KEY_ALGORITHM));
        } catch (final GeneralSecurityException e) {
            throw new IllegalStateException("Unable to derive the key to encrypt the stored passwords", e);
        } finally {
            keySpec.clearPassword();
        }
    }

    /**
     * Creates a new random salt, used to derive the key of a new database.
     *
     * @return the salt
     */
    public static byte[] newSalt() {
        final byte[] salt = new byte[SALT_LENGTH_BYTES];
        SECURE_RANDOM.nextBytes(salt);
        return salt;
    }

    /**
     * Whether the stored value is an encrypted password, rather than a password stored in plain text by an earlier version.
     *
     * @param storedValue the value stored in the database
     * @return {@code true} if the value is encrypted
     */
    public static boolean isEncrypted(final String storedValue) {
        return storedValue.startsWith(ENCRYPTED_PREFIX);
    }

    /**
     * Encrypts a password. An empty password (meaning none is saved) is kept empty.
     *
     * @param trackerName the name of the tracker the password belongs to
     * @param password    the password in plain text
     * @return the encrypted password, to be stored in the database
     */
    public String encrypt(final String trackerName, final String password) {
        if (password.isEmpty()) {
            return password;
        }

        try {
            final byte[] iv = new byte[IV_LENGTH_BYTES];
            SECURE_RANDOM.nextBytes(iv);
            final Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            cipher.updateAAD(associatedData(trackerName));
            final byte[] ciphertext = cipher.doFinal(password.getBytes(StandardCharsets.UTF_8));

            final byte[] stored = ByteBuffer.allocate(iv.length + ciphertext.length).put(iv).put(ciphertext).array();
            return ENCRYPTED_PREFIX + Base64.getEncoder().encodeToString(stored);
        } catch (final GeneralSecurityException e) {
            throw new IllegalStateException("Unable to encrypt the password", e);
        }
    }

    /**
     * Decrypts a password stored in the database. An empty value (meaning no password is saved) is returned as is.
     *
     * @param trackerName the name of the tracker the password belongs to
     * @param storedValue the encrypted password
     * @return the password in plain text
     * @throws IllegalStateException thrown if the password cannot be decrypted, such as when it was encrypted with a different key
     */
    public String decrypt(final String trackerName, final String storedValue) {
        if (storedValue.isEmpty()) {
            return storedValue;
        }
        if (!isEncrypted(storedValue)) {
            throw new IllegalStateException("The stored password for '%s' is not encrypted".formatted(trackerName));
        }

        try {
            final byte[] stored = Base64.getDecoder().decode(storedValue.substring(ENCRYPTED_PREFIX.length()));
            final Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(TAG_LENGTH_BITS, stored, 0, IV_LENGTH_BYTES));
            cipher.updateAAD(associatedData(trackerName));
            final byte[] password = cipher.doFinal(stored, IV_LENGTH_BYTES, stored.length - IV_LENGTH_BYTES);
            return new String(password, StandardCharsets.UTF_8);
        } catch (final GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException(
                "Unable to decrypt the stored password for '%s', it may have been encrypted with a different key".formatted(trackerName), e);
        }
    }

    // Tracker names are stored case-insensitively, so the associated data must not depend on the casing used
    private static byte[] associatedData(final String trackerName) {
        return trackerName.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8);
    }
}
