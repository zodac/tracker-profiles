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
import java.util.List;

/**
 * The result of the most recent execution of each tracker, in the {@code tracker_results} table.
 */
public final class TrackerResultRepository {

    private static final String SELECT_ALL = "SELECT name, finished_at, successful FROM tracker_results ORDER BY name";
    private static final String UPSERT = """
        INSERT INTO tracker_results (name, finished_at, successful) VALUES (?1, ?2, ?3)
        ON CONFLICT (name) DO UPDATE SET
            finished_at = excluded.finished_at,
            successful  = excluded.successful""";
    private static final String DELETE = "DELETE FROM tracker_results WHERE name = ?1";

    private final DatabaseConnection connection;

    private TrackerResultRepository(final DatabaseConnection connection) {
        this.connection = connection;
    }

    /**
     * Creates a {@link TrackerResultRepository}.
     *
     * @param connection the {@link DatabaseConnection}
     * @return the {@link TrackerResultRepository}
     */
    static TrackerResultRepository create(final DatabaseConnection connection) {
        return new TrackerResultRepository(connection);
    }

    /**
     * Retrieves the result of the most recent execution of each tracker, ordered by name.
     *
     * @return the {@link StoredTrackerResult}s
     * @throws SQLException thrown if the database cannot be queried
     */
    public List<StoredTrackerResult> findAll() throws SQLException {
        return connection.query(SELECT_ALL, resultSet -> new StoredTrackerResult(
            resultSet.getString("name"),
            resultSet.getLong("finished_at"),
            resultSet.getInt("successful") != 0
        ));
    }

    /**
     * Saves the result of the most recent execution of a tracker, replacing any previous result.
     *
     * @param trackerResult the {@link StoredTrackerResult}
     * @throws SQLException thrown if the database cannot be updated
     */
    public void save(final StoredTrackerResult trackerResult) throws SQLException {
        connection.update(UPSERT, statement -> {
            statement.setString(1, trackerResult.name());
            statement.setLong(2, trackerResult.finishedAt());
            statement.setInt(3, trackerResult.successful() ? 1 : 0);
        });
    }

    /**
     * Deletes the result of a tracker.
     *
     * @param name the tracker name
     * @return {@code true} if a result was deleted
     * @throws SQLException thrown if the database cannot be updated
     */
    boolean delete(final String name) throws SQLException {
        return connection.update(DELETE, statement -> statement.setString(1, name)) > 0;
    }
}
