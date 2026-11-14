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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jspecify.annotations.Nullable;

/**
 * SQLite-backed storage for the web UI, holding tracker credentials, settings that override environment variables, and the result of the most recent
 * execution of each tracker. Tracker passwords are encrypted with a {@link PasswordCipher}.
 *
 * <p>
 * A new {@link Connection} is opened for each operation, and all operations are serialised by a {@link Lock}, since the expected load is a single
 * user editing values in a browser.
 */
public final class ApplicationDatabase implements AutoCloseable {

    private static final Logger LOGGER = LogManager.getLogger();

    private static final String CREATE_TRACKERS_TABLE = """
        CREATE TABLE IF NOT EXISTS tracker_credentials (
            name     TEXT PRIMARY KEY COLLATE NOCASE,
            username TEXT    NOT NULL DEFAULT '',
            password TEXT    NOT NULL DEFAULT '',
            enabled  INTEGER NOT NULL DEFAULT 0
        )""";
    private static final String CREATE_SETTINGS_TABLE = """
        CREATE TABLE IF NOT EXISTS settings (
            name  TEXT PRIMARY KEY COLLATE NOCASE,
            value TEXT NOT NULL
        )""";

    private static final String CREATE_METADATA_TABLE = """
        CREATE TABLE IF NOT EXISTS metadata (
            name  TEXT PRIMARY KEY,
            value TEXT NOT NULL
        )""";
    private static final String SALT_METADATA = "password_salt";
    private static final String SELECT_METADATA = "SELECT value FROM metadata WHERE name = ?";
    private static final String INSERT_METADATA = "INSERT INTO metadata (name, value) VALUES (?, ?)";

    private static final String CREATE_RESULTS_TABLE = """
        CREATE TABLE IF NOT EXISTS tracker_results (
            name        TEXT PRIMARY KEY COLLATE NOCASE,
            finished_at INTEGER NOT NULL,
            successful  INTEGER NOT NULL
        )""";

    private static final String SELECT_ALL_CREDENTIALS = "SELECT name, username, password, enabled FROM tracker_credentials ORDER BY name";
    private static final String COUNT_CREDENTIALS = "SELECT COUNT(*) FROM tracker_credentials";
    // A NULL password keeps the existing password, so the UI never needs to send back a password it has not changed
    private static final String UPSERT_CREDENTIAL = """
        INSERT INTO tracker_credentials (name, username, password, enabled) VALUES (?, ?, COALESCE(?, ''), ?)
        ON CONFLICT(name) DO UPDATE SET
            username = excluded.username,
            password = COALESCE(?, tracker_credentials.password),
            enabled  = excluded.enabled""";
    private static final String DELETE_CREDENTIAL = "DELETE FROM tracker_credentials WHERE name = ?";
    private static final String SELECT_ALL_CREDENTIAL_STATUSES =
        "SELECT name, username, password <> '' AS has_password, enabled FROM tracker_credentials ORDER BY name";
    private static final String SELECT_HAS_PASSWORD = "SELECT 1 FROM tracker_credentials WHERE name = ? AND password <> ''";
    // A tracker can only be enabled with a username and password
    private static final String UPDATE_ENABLED = """
        UPDATE tracker_credentials SET enabled = ?
        WHERE name = ? AND enabled <> ? AND (? = 0 OR (username <> '' AND password <> ''))""";
    private static final String UPDATE_PASSWORD = "UPDATE tracker_credentials SET password = ? WHERE name = ?";

    private static final String SELECT_ALL_SETTINGS = "SELECT name, value FROM settings ORDER BY name";
    private static final String UPSERT_SETTING =
        "INSERT INTO settings (name, value) VALUES (?, ?) ON CONFLICT(name) DO UPDATE SET value = excluded.value";
    private static final String DELETE_SETTING = "DELETE FROM settings WHERE name = ?";

    private static final String SELECT_ALL_RESULTS = "SELECT name, finished_at, successful FROM tracker_results ORDER BY name";
    private static final String UPSERT_RESULT = """
        INSERT INTO tracker_results (name, finished_at, successful) VALUES (?, ?, ?)
        ON CONFLICT(name) DO UPDATE SET
            finished_at = excluded.finished_at,
            successful  = excluded.successful""";

    private final Lock lock = new ReentrantLock();
    // The only connection, opened with the database and used while holding the lock. SQLite allows a single writer, so a pool would not help
    private final Connection connection;
    private final PasswordCipher passwordCipher;

    private ApplicationDatabase(final Connection connection, final PasswordCipher passwordCipher) {
        this.connection = connection;
        this.passwordCipher = passwordCipher;
    }

    /**
     * Opens (creating if needed) the SQLite database at the given {@link Path}, and creates any missing tables. Passwords stored in plain text
     * (by an earlier version) are encrypted, and every stored password is checked to be decryptable with the key.
     *
     * @param databasePath the {@link Path} to the SQLite database file
     * @param secretKey    the secret used to encrypt the stored passwords (the {@code SECRET_KEY} environment variable)
     * @return the {@link ApplicationDatabase}
     * @throws IOException              thrown if the parent directory of the database cannot be created
     * @throws SQLException             thrown if the database cannot be opened or initialised
     * @throws IllegalArgumentException thrown if the secret is too short
     * @throws IllegalStateException    thrown if a stored password cannot be decrypted with the secret
     */
    public static ApplicationDatabase open(final Path databasePath, final String secretKey) throws IOException, SQLException {
        final Path absolutePath = databasePath.toAbsolutePath();
        final Path parent = absolutePath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        final Connection connection = DriverManager.getConnection("jdbc:sqlite:" + absolutePath); // NOPMD: CloseResource - closed by close()
        try {
            configure(connection);
            createTables(connection);
            final byte[] salt = findOrCreateSalt(connection);

            final ApplicationDatabase applicationDatabase = new ApplicationDatabase(connection, PasswordCipher.fromSecretKey(secretKey, salt));
            applicationDatabase.encryptPlainTextPasswords();
            // Fails at startup (rather than on the next execution) if the secret was changed
            applicationDatabase.findAllCredentials();
            LOGGER.debug("Opened database at '{}'", absolutePath);
            return applicationDatabase;
        } catch (final SQLException | RuntimeException e) {
            connection.close();
            throw e;
        }
    }

    // WAL with NORMAL synchronisation makes each (small, frequent) autosave from the web UI much cheaper than the default rollback journal, and
    // is still safe from corruption on a crash (only the last transactions may be lost on a power failure)
    private static void configure(final Connection connection) throws SQLException {
        try (final Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA journal_mode = WAL");
            statement.execute("PRAGMA synchronous = NORMAL");
        }
    }

    /**
     * Closes the connection to the database. The {@link ApplicationDatabase} cannot be used afterwards.
     *
     * @throws SQLException thrown if the connection cannot be closed
     */
    @Override
    public void close() throws SQLException {
        lock.lock();
        try {
            connection.close();
        } finally {
            lock.unlock();
        }
    }

    // The salt used to derive the key from the secret, created with the database
    private static byte[] findOrCreateSalt(final Connection connection) throws SQLException {
        try (final PreparedStatement selectStatement = connection.prepareStatement(SELECT_METADATA)) {
            selectStatement.setString(1, SALT_METADATA);
            try (final ResultSet resultSet = selectStatement.executeQuery()) {
                if (resultSet.next()) {
                    return Base64.getDecoder().decode(resultSet.getString("value"));
                }
            }
        }

        final byte[] salt = PasswordCipher.newSalt();
        try (final PreparedStatement insertStatement = connection.prepareStatement(INSERT_METADATA)) {
            insertStatement.setString(1, SALT_METADATA);
            insertStatement.setString(2, Base64.getEncoder().encodeToString(salt));
            insertStatement.executeUpdate();
        }
        return salt;
    }

    private void encryptPlainTextPasswords() throws SQLException {
        lock.lock();
        try {
            connection.setAutoCommit(false);
            try (final PreparedStatement selectStatement = connection.prepareStatement(SELECT_ALL_CREDENTIALS);
                final ResultSet resultSet = selectStatement.executeQuery();
                final PreparedStatement updateStatement = connection.prepareStatement(UPDATE_PASSWORD)) {
                int encrypted = 0;
                while (resultSet.next()) {
                    final String name = resultSet.getString("name");
                    final String password = resultSet.getString("password");
                    if (!password.isEmpty() && !PasswordCipher.isEncrypted(password)) {
                        updateStatement.setString(1, passwordCipher.encrypt(name, password));
                        updateStatement.setString(2, name);
                        updateStatement.addBatch();
                        encrypted++;
                    }
                }
                updateStatement.executeBatch();
                connection.commit();
                if (encrypted > 0) {
                    LOGGER.info("Encrypted {} stored password{}", encrypted, encrypted == 1 ? "" : "s");
                }
            } catch (final SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } finally {
            lock.unlock();
        }
    }

    private static void createTables(final Connection connection) throws SQLException {
        try (final Statement statement = connection.createStatement()) {
            statement.execute(CREATE_TRACKERS_TABLE);
            statement.execute(CREATE_SETTINGS_TABLE);
            statement.execute(CREATE_RESULTS_TABLE);
            statement.execute(CREATE_METADATA_TABLE);
        }
    }

    /**
     * Retrieves all stored tracker credentials, ordered by name.
     *
     * @return the {@link StoredTrackerCredential}s
     * @throws SQLException thrown if the database cannot be queried
     */
    public List<StoredTrackerCredential> findAllCredentials() throws SQLException {
        lock.lock();
        try (final PreparedStatement statement = connection.prepareStatement(SELECT_ALL_CREDENTIALS);
            final ResultSet resultSet = statement.executeQuery()) {
            final List<StoredTrackerCredential> credentials = new ArrayList<>();
            while (resultSet.next()) {
                final String name = resultSet.getString("name");
                credentials.add(new StoredTrackerCredential(
                    name,
                    resultSet.getString("username"),
                    passwordCipher.decrypt(name, resultSet.getString("password")),
                    resultSet.getInt("enabled") != 0
                ));
            }
            return credentials;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Checks whether any tracker credentials have been stored.
     *
     * @return {@code true} if at least one tracker credential exists
     * @throws SQLException thrown if the database cannot be queried
     */
    public boolean hasCredentials() throws SQLException {
        lock.lock();
        try (final PreparedStatement statement = connection.prepareStatement(COUNT_CREDENTIALS);
            final ResultSet resultSet = statement.executeQuery()) {
            return resultSet.next() && resultSet.getInt(1) > 0;
        } finally {
            lock.unlock();
        }
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
    public void saveCredential(final String name, final String username, final @Nullable String password, final boolean enabled)
        throws SQLException {
        lock.lock();
        try (final PreparedStatement statement = connection.prepareStatement(UPSERT_CREDENTIAL)) {
            bindCredential(statement, name, username, password, enabled);
            statement.executeUpdate();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Creates or updates the credentials for multiple trackers in a single transaction.
     *
     * @param credentials the {@link StoredTrackerCredential}s to save
     * @throws SQLException thrown if the database cannot be updated, in which case no credentials are saved
     */
    public void saveCredentials(final Collection<StoredTrackerCredential> credentials) throws SQLException {
        lock.lock();
        try {
            connection.setAutoCommit(false);
            try (final PreparedStatement statement = connection.prepareStatement(UPSERT_CREDENTIAL)) {
                for (final StoredTrackerCredential credential : credentials) {
                    bindCredential(statement, credential.name(), credential.username(), credential.password(), credential.enabled());
                    statement.addBatch();
                }
                statement.executeBatch();
                connection.commit();
            } catch (final SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } finally {
            lock.unlock();
        }
    }

    private void bindCredential(final PreparedStatement statement, final String name, final String username, final @Nullable String password,
        final boolean enabled) throws SQLException {
        final String storedPassword = password == null ? null : passwordCipher.encrypt(name, password);
        statement.setString(1, name);
        statement.setString(2, username);
        statement.setString(3, storedPassword);
        statement.setInt(4, enabled ? 1 : 0);
        statement.setString(5, storedPassword);
    }

    /**
     * Deletes the stored credentials for a tracker.
     *
     * @param name the tracker name
     * @throws SQLException thrown if the database cannot be updated
     */
    public void deleteCredential(final String name) throws SQLException {
        executeUpdate(DELETE_CREDENTIAL, name);
    }

    /**
     * Retrieves all settings overridden in the web UI.
     *
     * @return the overridden values, keyed by environment variable name
     * @throws SQLException thrown if the database cannot be queried
     */
    public Map<String, String> findAllSettings() throws SQLException {
        lock.lock();
        try (final PreparedStatement statement = connection.prepareStatement(SELECT_ALL_SETTINGS);
            final ResultSet resultSet = statement.executeQuery()) {
            final Map<String, String> settings = new LinkedHashMap<>();
            while (resultSet.next()) {
                settings.put(resultSet.getString("name"), resultSet.getString("value"));
            }
            return settings;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Replaces all settings overridden in the web UI with the provided values, in a single transaction.
     *
     * @param settings the overridden values, keyed by environment variable name
     * @throws SQLException thrown if the database cannot be updated, in which case no settings are changed
     */
    public void replaceSettings(final Map<String, String> settings) throws SQLException {
        lock.lock();
        try {
            connection.setAutoCommit(false);
            try (final PreparedStatement deleteStatement = connection.prepareStatement(DELETE_SETTING);
                final PreparedStatement upsertStatement = connection.prepareStatement(UPSERT_SETTING)) {
                for (final String existingName : findAllSettingNames(connection)) {
                    if (!settings.containsKey(existingName)) {
                        deleteStatement.setString(1, existingName);
                        deleteStatement.addBatch();
                    }
                }
                for (final Map.Entry<String, String> setting : settings.entrySet()) {
                    upsertStatement.setString(1, setting.getKey());
                    upsertStatement.setString(2, setting.getValue());
                    upsertStatement.addBatch();
                }
                deleteStatement.executeBatch();
                upsertStatement.executeBatch();
                connection.commit();
            } catch (final SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Retrieves the result of the most recent execution of each tracker, ordered by name.
     *
     * @return the {@link StoredTrackerResult}s
     * @throws SQLException thrown if the database cannot be queried
     */
    public List<StoredTrackerResult> findAllResults() throws SQLException {
        lock.lock();
        try (final PreparedStatement statement = connection.prepareStatement(SELECT_ALL_RESULTS);
            final ResultSet resultSet = statement.executeQuery()) {
            final List<StoredTrackerResult> results = new ArrayList<>();
            while (resultSet.next()) {
                results.add(new StoredTrackerResult(
                    resultSet.getString("name"),
                    resultSet.getLong("finished_at"),
                    resultSet.getInt("successful") != 0
                ));
            }
            return results;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Saves the result of the most recent execution of a tracker, replacing any previous result.
     *
     * @param trackerResult the {@link StoredTrackerResult}
     * @throws SQLException thrown if the database cannot be updated
     */
    public void saveResult(final StoredTrackerResult trackerResult) throws SQLException {
        lock.lock();
        try (final PreparedStatement statement = connection.prepareStatement(UPSERT_RESULT)) {
            statement.setString(1, trackerResult.name());
            statement.setLong(2, trackerResult.finishedAt());
            statement.setInt(3, trackerResult.successful() ? 1 : 0);
            statement.executeUpdate();
        } finally {
            lock.unlock();
        }
    }

    private static List<String> findAllSettingNames(final Connection connection) throws SQLException {
        try (final PreparedStatement statement = connection.prepareStatement(SELECT_ALL_SETTINGS);
            final ResultSet resultSet = statement.executeQuery()) {
            final List<String> names = new ArrayList<>();
            while (resultSet.next()) {
                names.add(resultSet.getString("name"));
            }
            return names;
        }
    }

    /**
     * Retrieves the {@link TrackerCredentialStatus} of every stored tracker, without decrypting any password.
     *
     * @return the {@link TrackerCredentialStatus} of each tracker, ordered by name
     * @throws SQLException thrown if the database cannot be read
     */
    public List<TrackerCredentialStatus> findAllCredentialStatuses() throws SQLException {
        lock.lock();
        try (final PreparedStatement statement = connection.prepareStatement(SELECT_ALL_CREDENTIAL_STATUSES);
            final ResultSet resultSet = statement.executeQuery()) {
            final List<TrackerCredentialStatus> statuses = new ArrayList<>();
            while (resultSet.next()) {
                statuses.add(new TrackerCredentialStatus(
                    resultSet.getString("name"),
                    resultSet.getString("username"),
                    resultSet.getInt("has_password") != 0,
                    resultSet.getInt("enabled") != 0
                ));
            }
            return statuses;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Whether a password is stored for the tracker, without decrypting it.
     *
     * @param name the tracker name (case-insensitive)
     * @return {@code true} if a password is stored
     * @throws SQLException thrown if the database cannot be read
     */
    public boolean hasPassword(final String name) throws SQLException {
        lock.lock();
        try (final PreparedStatement statement = connection.prepareStatement(SELECT_HAS_PASSWORD)) {
            statement.setString(1, name);
            try (final ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Enables or disables several trackers in a single transaction. A tracker without both a username and a password is not enabled, and a tracker
     * already in the requested state is not changed.
     *
     * @param names   the tracker names (case-insensitive)
     * @param enabled whether to enable the trackers
     * @return the names of the trackers that were changed, in the order provided
     * @throws SQLException thrown if the database cannot be updated, in which case no tracker is changed
     */
    public List<String> changeEnabled(final Collection<String> names, final boolean enabled) throws SQLException {
        final int enabledValue = enabled ? 1 : 0;
        return updateEach(UPDATE_ENABLED, names, (statement, name) -> {
            statement.setInt(1, enabledValue);
            statement.setString(2, name);
            statement.setInt(3, enabledValue);
            statement.setInt(4, enabledValue);
        });
    }

    /**
     * Deletes the stored credentials of several trackers in a single transaction.
     *
     * @param names the tracker names (case-insensitive)
     * @return the names of the trackers whose credentials were deleted, in the order provided
     * @throws SQLException thrown if the database cannot be updated, in which case no credentials are deleted
     */
    public List<String> deleteCredentials(final Collection<String> names) throws SQLException {
        return updateEach(DELETE_CREDENTIAL, names, (statement, name) -> statement.setString(1, name));
    }

    // Executes the statement once per name in a single transaction, returning the names for which a row was changed
    private List<String> updateEach(final String sql, final Collection<String> names, final StatementBinder binder) throws SQLException {
        lock.lock();
        try {
            connection.setAutoCommit(false);
            try (final PreparedStatement statement = connection.prepareStatement(sql)) {
                final List<String> orderedNames = List.copyOf(names);
                for (final String name : orderedNames) {
                    binder.bind(statement, name);
                    statement.addBatch();
                }
                final int[] updateCounts = statement.executeBatch();
                connection.commit();

                final List<String> changed = new ArrayList<>();
                for (int i = 0; i < updateCounts.length; i++) {
                    if (updateCounts[i] > 0) {
                        changed.add(orderedNames.get(i));
                    }
                }
                return changed;
            } catch (final SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } finally {
            lock.unlock();
        }
    }

    @FunctionalInterface
    private interface StatementBinder {

        void bind(PreparedStatement statement, String name) throws SQLException;
    }

    private void executeUpdate(final String sql, final String parameter) throws SQLException {
        lock.lock();
        try (final PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, parameter);
            statement.executeUpdate();
        } finally {
            lock.unlock();
        }
    }

}
