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
 * The latest screenshots of each tracker (the profile screenshots of its latest successful execution, and the failure screenshot of its latest
 * execution if it failed), in the {@code tracker_screenshots} table.
 */
public final class TrackerScreenshotRepository {

    private static final String SELECT_ALL = "SELECT name, id, position, path, saved_at FROM tracker_screenshots ORDER BY name, position";
    private static final String DELETE_FOR_TRACKER = "DELETE FROM tracker_screenshots WHERE name = ?1";
    private static final String INSERT = "INSERT INTO tracker_screenshots (name, id, position, path, saved_at) "
        + "VALUES (?1, ?2, ?3, ?4, ?5)";

    private final DatabaseConnection connection;

    private TrackerScreenshotRepository(final DatabaseConnection connection) {
        this.connection = connection;
    }

    /**
     * Creates a {@link TrackerScreenshotRepository}.
     *
     * @param connection the {@link DatabaseConnection}
     * @return the {@link TrackerScreenshotRepository}
     */
    static TrackerScreenshotRepository create(final DatabaseConnection connection) {
        return new TrackerScreenshotRepository(connection);
    }

    /**
     * Retrieves the latest screenshots of every tracker, by tracker name, then in the order they are shown.
     *
     * @return the {@link StoredTrackerScreenshot}s
     * @throws SQLException thrown if the database cannot be queried
     */
    public List<StoredTrackerScreenshot> findAll() throws SQLException {
        return connection.query(SELECT_ALL, resultSet -> new StoredTrackerScreenshot(
            resultSet.getString("name"),
            resultSet.getString("id"),
            resultSet.getInt("position"),
            resultSet.getString("path"),
            resultSet.getLong("saved_at")
        ));
    }

    /**
     * Replaces every stored screenshot of a tracker, in a single transaction.
     *
     * @param trackerName the tracker name
     * @param screenshots the latest {@link StoredTrackerScreenshot}s of the tracker
     * @throws SQLException thrown if the database cannot be updated, in which case the previous screenshots are kept
     */
    public void replace(final String trackerName, final List<StoredTrackerScreenshot> screenshots) throws SQLException {
        connection.inTransaction(() -> {
            connection.update(DELETE_FOR_TRACKER, statement -> statement.setString(1, trackerName));
            return connection.batch(INSERT, screenshots, (statement, screenshot) -> {
                statement.setString(1, screenshot.name());
                statement.setString(2, screenshot.id());
                statement.setInt(3, screenshot.position());
                statement.setString(4, screenshot.path());
                statement.setLong(5, screenshot.savedAt());
            });
        });
    }

    /**
     * Deletes every stored screenshot of a tracker (the screenshot files are kept).
     *
     * @param trackerName the tracker name
     * @return {@code true} if any screenshot was deleted
     * @throws SQLException thrown if the database cannot be updated
     */
    boolean delete(final String trackerName) throws SQLException {
        return connection.update(DELETE_FOR_TRACKER, statement -> statement.setString(1, trackerName)) > 0;
    }
}
