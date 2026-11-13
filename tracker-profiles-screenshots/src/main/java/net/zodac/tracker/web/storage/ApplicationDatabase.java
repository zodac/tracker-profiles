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
 * SQLite-backed storage for the web UI, holding tracker credentials and settings that override environment variables.
 *
 * <p>
 * A new {@link Connection} is opened for each operation, and all operations are serialised by a {@link Lock}, since the expected load is a single
 * user editing values in a browser.
 */
public final class ApplicationDatabase {

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

    private static final String SELECT_ALL_SETTINGS = "SELECT name, value FROM settings ORDER BY name";
    private static final String UPSERT_SETTING =
        "INSERT INTO settings (name, value) VALUES (?, ?) ON CONFLICT(name) DO UPDATE SET value = excluded.value";
    private static final String DELETE_SETTING = "DELETE FROM settings WHERE name = ?";

    private final Lock lock = new ReentrantLock();
    private final String jdbcUrl;

    private ApplicationDatabase(final String jdbcUrl) {
        this.jdbcUrl = jdbcUrl;
    }

    /**
     * Opens (creating if needed) the SQLite database at the given {@link Path}, and creates any missing tables.
     *
     * @param databasePath the {@link Path} to the SQLite database file
     * @return the {@link ApplicationDatabase}
     * @throws IOException  thrown if the parent directory of the database cannot be created
     * @throws SQLException thrown if the database cannot be opened or initialised
     */
    public static ApplicationDatabase open(final Path databasePath) throws IOException, SQLException {
        final Path absolutePath = databasePath.toAbsolutePath();
        final Path parent = absolutePath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        final ApplicationDatabase applicationDatabase = new ApplicationDatabase("jdbc:sqlite:" + absolutePath);
        applicationDatabase.createTables();
        LOGGER.debug("Opened database at '{}'", absolutePath);
        return applicationDatabase;
    }

    private void createTables() throws SQLException {
        lock.lock();
        try (final Connection connection = connect();
            final Statement statement = connection.createStatement()) {
            statement.execute(CREATE_TRACKERS_TABLE);
            statement.execute(CREATE_SETTINGS_TABLE);
        } finally {
            lock.unlock();
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
        try (final Connection connection = connect();
            final PreparedStatement statement = connection.prepareStatement(SELECT_ALL_CREDENTIALS);
            final ResultSet resultSet = statement.executeQuery()) {
            final List<StoredTrackerCredential> credentials = new ArrayList<>();
            while (resultSet.next()) {
                credentials.add(new StoredTrackerCredential(
                    resultSet.getString("name"),
                    resultSet.getString("username"),
                    resultSet.getString("password"),
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
        try (final Connection connection = connect();
            final PreparedStatement statement = connection.prepareStatement(COUNT_CREDENTIALS);
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
     * @param password the password, or {@code null} to keep any existing password
     * @param enabled  whether the tracker should be executed
     * @throws SQLException thrown if the database cannot be updated
     */
    public void saveCredential(final String name, final String username, final @Nullable String password, final boolean enabled)
        throws SQLException {
        lock.lock();
        try (final Connection connection = connect();
            final PreparedStatement statement = connection.prepareStatement(UPSERT_CREDENTIAL)) {
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
        try (final Connection connection = connect()) {
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
            }
        } finally {
            lock.unlock();
        }
    }

    private static void bindCredential(final PreparedStatement statement, final String name, final String username, final @Nullable String password,
        final boolean enabled) throws SQLException {
        statement.setString(1, name);
        statement.setString(2, username);
        statement.setString(3, password);
        statement.setInt(4, enabled ? 1 : 0);
        statement.setString(5, password);
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
        try (final Connection connection = connect();
            final PreparedStatement statement = connection.prepareStatement(SELECT_ALL_SETTINGS);
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
        try (final Connection connection = connect()) {
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
            }
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

    private void executeUpdate(final String sql, final String parameter) throws SQLException {
        lock.lock();
        try (final Connection connection = connect();
            final PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, parameter);
            statement.executeUpdate();
        } finally {
            lock.unlock();
        }
    }

    private Connection connect() throws SQLException {
        return DriverManager.getConnection(jdbcUrl);
    }
}
