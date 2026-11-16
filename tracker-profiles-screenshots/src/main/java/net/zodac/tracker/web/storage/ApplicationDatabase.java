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
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.output.MigrateResult;

/**
 * The SQLite database used by the web UI, holding the tracker credentials ({@link #credentials()}), the settings overridden in the web UI
 * ({@link #settings()}), the result of each tracker's most recent execution ({@link #results()}) and the screenshots of each tracker's latest
 * successful execution ({@link #screenshots()}).
 *
 * <p>
 * The schema is versioned by Flyway: each change is a new migration in {@code src/main/resources/db/migration}
 * ({@code V<version>__<description>.sql}), applied in order when the database is opened. A released migration must never be changed.
 */
public final class ApplicationDatabase implements AutoCloseable {

    private static final Logger LOGGER = LogManager.getLogger();

    private static final String MIGRATIONS_LOCATION = "classpath:db/migration";
    // A database created before the schema was versioned (with the tables of V1 already created) is baselined before V1, so every migration runs
    private static final String UNVERSIONED_BASELINE = "0";

    private static final String SALT_METADATA = "password_salt";
    private static final String SELECT_METADATA = "SELECT value FROM metadata WHERE name = ?1";
    private static final String INSERT_METADATA = "INSERT INTO metadata (name, value) VALUES (?1, ?2) ON CONFLICT (name) DO NOTHING";

    private final DatabaseConnection connection;
    private final TrackerCredentialRepository credentials;
    private final SettingRepository settings;
    private final TrackerResultRepository results;
    private final TrackerScreenshotRepository screenshots;

    private ApplicationDatabase(final DatabaseConnection connection, final TrackerCredentialRepository credentials, final SettingRepository settings,
        final TrackerResultRepository results, final TrackerScreenshotRepository screenshots) {
        this.connection = connection;
        this.credentials = credentials;
        this.settings = settings;
        this.results = results;
        this.screenshots = screenshots;
    }

    /**
     * Opens (creating if needed) the SQLite database at the given {@link Path}, and migrates its schema to the latest version. Passwords stored in
     * plain text (by an earlier version) are encrypted, and every stored password is checked to be decryptable with the key.
     *
     * @param databasePath the {@link Path} to the SQLite database file
     * @param secretKey    the secret used to encrypt the stored passwords (the {@code SECRET_KEY} environment variable)
     * @return the {@link ApplicationDatabase}
     * @throws IOException              thrown if the parent directory of the database cannot be created
     * @throws SQLException             thrown if the database cannot be opened, migrated or initialised
     * @throws IllegalArgumentException thrown if the secret is too short
     * @throws IllegalStateException    thrown if a stored password cannot be decrypted with the secret
     */
    public static ApplicationDatabase open(final Path databasePath, final String secretKey) throws IOException, SQLException {
        final Path absolutePath = databasePath.toAbsolutePath();
        final Path parent = absolutePath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        final String jdbcUrl = "jdbc:sqlite:" + absolutePath;
        migrate(jdbcUrl);

        final DatabaseConnection connection = DatabaseConnection.open(jdbcUrl);
        try {
            final PasswordCipher passwordCipher = PasswordCipher.fromSecretKey(secretKey, findOrCreateSalt(connection));
            final TrackerCredentialRepository credentials = TrackerCredentialRepository.create(connection, passwordCipher);
            credentials.encryptPlainTextPasswords();
            // Fails at startup (rather than on the next execution) if the secret was changed
            credentials.findAll();

            LOGGER.debug("Opened database at '{}'", absolutePath);
            return new ApplicationDatabase(connection, credentials, SettingRepository.create(connection), TrackerResultRepository.create(connection),
                TrackerScreenshotRepository.create(connection));
        } catch (final SQLException | RuntimeException e) {
            connection.close();
            throw e;
        }
    }

    // Flyway opens (and closes) its own connection, before the one used by the application is opened
    private static void migrate(final String jdbcUrl) throws SQLException {
        try {
            final MigrateResult result = Flyway.configure()
                .dataSource(jdbcUrl, "", "")
                .locations(MIGRATIONS_LOCATION)
                .baselineOnMigrate(true)
                .baselineVersion(UNVERSIONED_BASELINE)
                .load()
                .migrate();
            if (result.migrationsExecuted > 0) {
                LOGGER.info("Migrated the database to version {}", result.targetSchemaVersion);
            } else {
                LOGGER.debug("Database is at version {}", result.initialSchemaVersion);
            }
        } catch (final FlywayException e) {
            throw new SQLException("Unable to migrate the database: %s".formatted(e.getMessage()), e);
        }
    }

    // The salt used to derive the key from the secret, created with the database
    private static byte[] findOrCreateSalt(final DatabaseConnection connection) throws SQLException {
        final String newSalt = Base64.getEncoder().encodeToString(PasswordCipher.newSalt());
        connection.update(INSERT_METADATA, statement -> {
            statement.setString(1, SALT_METADATA);
            statement.setString(2, newSalt);
        });

        final List<String> salts = connection.query(SELECT_METADATA, statement -> statement.setString(1, SALT_METADATA),
            resultSet -> resultSet.getString("value"));
        return Base64.getDecoder().decode(salts.getFirst());
    }

    /**
     * The stored credentials of each tracker.
     *
     * @return the {@link TrackerCredentialRepository}
     */
    public TrackerCredentialRepository credentials() {
        return credentials;
    }

    /**
     * The settings overridden in the web UI.
     *
     * @return the {@link SettingRepository}
     */
    public SettingRepository settings() {
        return settings;
    }

    /**
     * The result of each tracker's most recent execution.
     *
     * @return the {@link TrackerResultRepository}
     */
    public TrackerResultRepository results() {
        return results;
    }

    /**
     * The profile screenshots of each tracker's latest successful execution.
     *
     * @return the {@link TrackerScreenshotRepository}
     */
    public TrackerScreenshotRepository screenshots() {
        return screenshots;
    }

    /**
     * Removes everything stored for several trackers (their credentials, the result of their most recent execution and their latest screenshots)
     * in a single transaction. The screenshot files themselves are kept.
     *
     * @param names the tracker names
     * @return the names of the trackers for which anything was stored (and removed), in the order provided
     * @throws SQLException thrown if the database cannot be updated, in which case nothing is removed
     */
    public List<String> removeTrackers(final Collection<String> names) throws SQLException {
        return connection.inTransaction(() -> {
            final List<String> removed = new ArrayList<>();
            for (final String name : names) {
                // Each is deleted, even once one has been found
                final boolean hadCredentials = credentials.deleteIfStored(name);
                final boolean hadResult = results.delete(name);
                final boolean hadScreenshots = screenshots.delete(name);
                if (hadCredentials || hadResult || hadScreenshots) {
                    removed.add(name);
                }
            }
            return removed;
        });
    }

    /**
     * Closes the connection to the database. The {@link ApplicationDatabase} cannot be used afterwards.
     *
     * @throws SQLException thrown if the connection cannot be closed
     */
    @Override
    public void close() throws SQLException {
        connection.close();
    }
}
