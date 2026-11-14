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

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jspecify.annotations.Nullable;

/**
 * The stored credentials of each tracker, in the {@code tracker_credentials} table. Passwords are encrypted by the {@link PasswordCipher}, and only
 * decrypted by {@link #findAll()} (for an execution). Tracker names are case-insensitive.
 */
public final class TrackerCredentialRepository {

    private static final Logger LOGGER = LogManager.getLogger();

    private static final String SELECT_ALL = "SELECT name, username, password, enabled FROM tracker_credentials ORDER BY name";
    private static final String SELECT_ALL_STATUSES =
        "SELECT name, username, password <> '' AS has_password, enabled FROM tracker_credentials ORDER BY name";
    private static final String SELECT_HAS_PASSWORD = "SELECT 1 FROM tracker_credentials WHERE name = ?1 AND password <> ''";
    // A NULL password (?3) keeps the existing password, so the UI never needs to send back a password it has not changed
    private static final String UPSERT = """
        INSERT INTO tracker_credentials (name, username, password, enabled) VALUES (?1, ?2, COALESCE(?3, ''), ?4)
        ON CONFLICT (name) DO UPDATE SET
            username = excluded.username,
            password = COALESCE(?3, password),
            enabled  = excluded.enabled""";
    private static final String DELETE = "DELETE FROM tracker_credentials WHERE name = ?1";
    // A tracker can only be enabled with a username and password, and a tracker already in the requested state is not counted as changed
    private static final String UPDATE_ENABLED = """
        UPDATE tracker_credentials SET enabled = ?2
        WHERE name = ?1 AND enabled <> ?2 AND (?2 = 0 OR (username <> '' AND password <> ''))""";
    private static final String UPDATE_PASSWORD = "UPDATE tracker_credentials SET password = ?2 WHERE name = ?1";

    private final DatabaseConnection connection;
    private final PasswordCipher passwordCipher;

    private TrackerCredentialRepository(final DatabaseConnection connection, final PasswordCipher passwordCipher) {
        this.connection = connection;
        this.passwordCipher = passwordCipher;
    }

    /**
     * Creates a {@link TrackerCredentialRepository}.
     *
     * @param connection     the {@link DatabaseConnection}
     * @param passwordCipher the {@link PasswordCipher} encrypting the stored passwords
     * @return the {@link TrackerCredentialRepository}
     */
    static TrackerCredentialRepository create(final DatabaseConnection connection, final PasswordCipher passwordCipher) {
        return new TrackerCredentialRepository(connection, passwordCipher);
    }

    /**
     * Encrypts any password stored in plain text (by an earlier version), in a single transaction.
     *
     * @throws SQLException thrown if the database cannot be updated, in which case no password is changed
     */
    void encryptPlainTextPasswords() throws SQLException {
        final List<PlainTextPassword> plainTextPasswords = connection
            .query(SELECT_ALL, resultSet -> new PlainTextPassword(resultSet.getString("name"), resultSet.getString("password")))
            .stream()
            .filter(stored -> !stored.password().isEmpty() && !PasswordCipher.isEncrypted(stored.password()))
            .toList();
        if (plainTextPasswords.isEmpty()) {
            return;
        }

        connection.batch(UPDATE_PASSWORD, plainTextPasswords, (statement, stored) -> {
            statement.setString(1, stored.name());
            statement.setString(2, passwordCipher.encrypt(stored.name(), stored.password()));
        });
        LOGGER.info("Encrypted {} stored password{}", plainTextPasswords.size(), plainTextPasswords.size() == 1 ? "" : "s");
    }

    /**
     * Retrieves all stored tracker credentials, with their decrypted passwords, ordered by name.
     *
     * @return the {@link StoredTrackerCredential}s
     * @throws SQLException          thrown if the database cannot be queried
     * @throws IllegalStateException thrown if a password cannot be decrypted (the secret has changed)
     */
    public List<StoredTrackerCredential> findAll() throws SQLException {
        return connection.query(SELECT_ALL, resultSet -> {
            final String name = resultSet.getString("name");
            return new StoredTrackerCredential(
                name,
                resultSet.getString("username"),
                passwordCipher.decrypt(name, resultSet.getString("password")),
                isTrue(resultSet, "enabled")
            );
        });
    }

    /**
     * Retrieves the {@link TrackerCredentialStatus} of every stored tracker, without decrypting any password.
     *
     * @return the {@link TrackerCredentialStatus} of each tracker, ordered by name
     * @throws SQLException thrown if the database cannot be queried
     */
    public List<TrackerCredentialStatus> findAllStatuses() throws SQLException {
        return connection.query(SELECT_ALL_STATUSES, resultSet -> new TrackerCredentialStatus(
            resultSet.getString("name"),
            resultSet.getString("username"),
            isTrue(resultSet, "has_password"),
            isTrue(resultSet, "enabled")
        ));
    }

    /**
     * Whether a password is stored for the tracker, without decrypting it.
     *
     * @param name the tracker name
     * @return {@code true} if a password is stored
     * @throws SQLException thrown if the database cannot be queried
     */
    public boolean hasPassword(final String name) throws SQLException {
        return connection.exists(SELECT_HAS_PASSWORD, statement -> statement.setString(1, name));
    }

    /**
     * Creates or updates the credentials for a tracker.
     *
     * @param name     the tracker name
     * @param username the username or email
     * @param password the password, {@code null} to keep any existing password, or empty to remove it
     * @param enabled  whether the tracker should be executed
     * @throws SQLException thrown if the database cannot be updated
     */
    public void save(final String name, final String username, final @Nullable String password, final boolean enabled) throws SQLException {
        connection.update(UPSERT, statement -> bindUpsert(statement, name, username, password, enabled));
    }

    /**
     * Creates or updates the credentials for multiple trackers in a single transaction.
     *
     * @param credentials the {@link StoredTrackerCredential}s to save
     * @throws SQLException thrown if the database cannot be updated, in which case no credentials are saved
     */
    public void saveAll(final Collection<StoredTrackerCredential> credentials) throws SQLException {
        connection.batch(UPSERT, List.copyOf(credentials), (statement, credential) ->
            bindUpsert(statement, credential.name(), credential.username(), credential.password(), credential.enabled()));
    }

    /**
     * Deletes the stored credentials of a tracker.
     *
     * @param name the tracker name
     * @throws SQLException thrown if the database cannot be updated
     */
    public void delete(final String name) throws SQLException {
        connection.update(DELETE, statement -> statement.setString(1, name));
    }

    /**
     * Deletes the stored credentials of several trackers in a single transaction.
     *
     * @param names the tracker names
     * @return the names of the trackers whose credentials were deleted, in the order provided
     * @throws SQLException thrown if the database cannot be updated, in which case no credentials are deleted
     */
    public List<String> deleteAll(final Collection<String> names) throws SQLException {
        return changed(names, DELETE, (statement, name) -> statement.setString(1, name));
    }

    /**
     * Enables or disables several trackers in a single transaction. A tracker without both a username and a password is not enabled, and a tracker
     * already in the requested state is not changed.
     *
     * @param names   the tracker names
     * @param enabled whether to enable the trackers
     * @return the names of the trackers that were changed, in the order provided
     * @throws SQLException thrown if the database cannot be updated, in which case no tracker is changed
     */
    public List<String> changeEnabled(final Collection<String> names, final boolean enabled) throws SQLException {
        return changed(names, UPDATE_ENABLED, (statement, name) -> {
            statement.setString(1, name);
            statement.setInt(2, enabled ? 1 : 0);
        });
    }

    // Executes the statement once per name in a single transaction, returning the names for which a row was changed
    private List<String> changed(final Collection<String> names, final String sql, final DatabaseConnection.ItemBinder<String> binder)
        throws SQLException {
        final List<String> orderedNames = List.copyOf(names);
        final int[] updateCounts = connection.batch(sql, orderedNames, binder);

        final List<String> changed = new ArrayList<>();
        for (int i = 0; i < updateCounts.length; i++) {
            if (updateCounts[i] > 0) {
                changed.add(orderedNames.get(i));
            }
        }
        return changed;
    }

    // A null password keeps the stored password
    private void bindUpsert(final PreparedStatement statement, final String name, final String username, final @Nullable String password,
        final boolean enabled) throws SQLException {
        statement.setString(1, name);
        statement.setString(2, username);
        statement.setString(3, password == null ? null : passwordCipher.encrypt(name, password));
        statement.setInt(4, enabled ? 1 : 0);
    }

    private static boolean isTrue(final ResultSet resultSet, final String column) throws SQLException {
        return resultSet.getInt(column) != 0;
    }

    // A stored password, as read before it is known whether it is encrypted
    private record PlainTextPassword(String name, String password) {

        @Override
        public String toString() {
            return "PlainTextPassword[name=%s, password=****]".formatted(name);
        }
    }
}
