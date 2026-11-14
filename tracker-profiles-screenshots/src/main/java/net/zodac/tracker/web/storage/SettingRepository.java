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

import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The settings overridden in the web UI, in the {@code settings} table, keyed by environment variable name.
 */
public final class SettingRepository {

    private static final String SELECT_ALL = "SELECT name, value FROM settings ORDER BY name";
    private static final String DELETE_ALL = "DELETE FROM settings";
    private static final String INSERT = "INSERT INTO settings (name, value) VALUES (?1, ?2)";

    private final DatabaseConnection connection;

    private SettingRepository(final DatabaseConnection connection) {
        this.connection = connection;
    }

    /**
     * Creates a {@link SettingRepository}.
     *
     * @param connection the {@link DatabaseConnection}
     * @return the {@link SettingRepository}
     */
    static SettingRepository create(final DatabaseConnection connection) {
        return new SettingRepository(connection);
    }

    /**
     * Retrieves all settings overridden in the web UI.
     *
     * @return the overridden values, keyed by environment variable name, ordered by name
     * @throws SQLException thrown if the database cannot be queried
     */
    public Map<String, String> findAll() throws SQLException {
        final Map<String, String> settings = new LinkedHashMap<>();
        for (final Map.Entry<String, String> setting : connection.query(SELECT_ALL,
            resultSet -> Map.entry(resultSet.getString("name"), resultSet.getString("value")))) {
            settings.put(setting.getKey(), setting.getValue());
        }
        return settings;
    }

    /**
     * Replaces all settings overridden in the web UI with the provided values, in a single transaction.
     *
     * @param settings the overridden values, keyed by environment variable name
     * @throws SQLException thrown if the database cannot be updated, in which case no settings are changed
     */
    public void replaceAll(final Map<String, String> settings) throws SQLException {
        final List<Map.Entry<String, String>> entries = List.copyOf(settings.entrySet());
        connection.inTransaction(() -> {
            connection.update(DELETE_ALL);
            return connection.batch(INSERT, entries, (statement, setting) -> {
                statement.setString(1, setting.getKey());
                statement.setString(2, setting.getValue());
            });
        });
    }
}
