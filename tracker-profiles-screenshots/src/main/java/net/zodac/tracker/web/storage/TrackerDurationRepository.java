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
 * The durations of the most recent successful executions of each tracker, in the {@code tracker_durations} table, from which the duration of its
 * next execution is estimated. Only the latest {@link #KEPT_DURATIONS} of each tracker are kept, and a tracker has an estimate (the average of
 * them) once at least {@link #MINIMUM_DURATIONS} are stored.
 */
public final class TrackerDurationRepository {

    /**
     * The number of durations kept for each tracker, from its most recent successful executions.
     */
    public static final int KEPT_DURATIONS = 5;

    /**
     * The number of durations a tracker needs before its duration is estimated.
     */
    public static final int MINIMUM_DURATIONS = 3;

    private static final String SELECT_ESTIMATES = """
        SELECT name, CAST(ROUND(AVG(duration)) AS INTEGER) AS estimated_duration, COUNT(*) AS executions
        FROM tracker_durations
        GROUP BY name
        HAVING COUNT(*) >= ?1
        ORDER BY name""";
    private static final String INSERT = """
        INSERT INTO tracker_durations (name, finished_at, duration) VALUES (?1, ?2, ?3)
        ON CONFLICT (name, finished_at) DO UPDATE SET duration = excluded.duration""";
    private static final String DELETE_OLDEST = """
        DELETE FROM tracker_durations
        WHERE name = ?1
        AND finished_at NOT IN (SELECT finished_at FROM tracker_durations WHERE name = ?1 ORDER BY finished_at DESC LIMIT ?2)""";
    private static final String DELETE = "DELETE FROM tracker_durations WHERE name = ?1";
    private static final String SELECT_ALL = "SELECT name, finished_at, duration FROM tracker_durations ORDER BY name, finished_at";

    private final DatabaseConnection connection;

    private TrackerDurationRepository(final DatabaseConnection connection) {
        this.connection = connection;
    }

    /**
     * Creates a {@link TrackerDurationRepository}.
     *
     * @param connection the {@link DatabaseConnection}
     * @return the {@link TrackerDurationRepository}
     */
    static TrackerDurationRepository create(final DatabaseConnection connection) {
        return new TrackerDurationRepository(connection);
    }

    /**
     * Retrieves the estimated duration of each tracker with at least {@link #MINIMUM_DURATIONS} stored, ordered by name.
     *
     * @return the {@link TrackerDurationEstimate}s
     * @throws SQLException thrown if the database cannot be queried
     */
    public List<TrackerDurationEstimate> findEstimates() throws SQLException {
        return connection.query(SELECT_ESTIMATES, statement -> statement.setInt(1, MINIMUM_DURATIONS), resultSet -> new TrackerDurationEstimate(
            resultSet.getString("name"),
            resultSet.getLong("estimated_duration"),
            resultSet.getInt("executions")
        ));
    }

    /**
     * Saves the duration of a successful execution of a tracker, removing its oldest durations so only the latest {@link #KEPT_DURATIONS} are kept,
     * in a single transaction.
     *
     * @param name       the tracker name
     * @param finishedAt when the execution finished, as epoch milliseconds
     * @param duration   the duration of the execution in milliseconds, excluding any time spent waiting for a user input
     * @throws SQLException thrown if the database cannot be updated, in which case nothing is changed
     */
    public void add(final String name, final long finishedAt, final long duration) throws SQLException {
        connection.inTransaction(() -> {
            connection.update(INSERT, statement -> {
                statement.setString(1, name);
                statement.setLong(2, finishedAt);
                statement.setLong(3, Math.max(0L, duration));
            });
            return connection.update(DELETE_OLDEST, statement -> {
                statement.setString(1, name);
                statement.setInt(2, KEPT_DURATIONS);
            });
        });
    }

    /**
     * Deletes every duration of a tracker.
     *
     * @param name the tracker name
     * @return {@code true} if any duration was deleted
     * @throws SQLException thrown if the database cannot be updated
     */
    boolean delete(final String name) throws SQLException {
        return connection.update(DELETE, statement -> statement.setString(1, name)) > 0;
    }

    /**
     * Retrieves every stored duration, for a {@link DatabaseBackup}.
     *
     * @return the {@link StoredTrackerDuration}s, ordered by tracker name, then from the oldest
     * @throws SQLException thrown if the database cannot be read
     */
    List<StoredTrackerDuration> findAll() throws SQLException {
        return connection.query(SELECT_ALL, resultSet -> new StoredTrackerDuration(
            resultSet.getString("name"),
            resultSet.getLong("finished_at"),
            resultSet.getLong("duration")
        ));
    }

    /**
     * Replaces the stored durations of every tracker with any duration in {@code trackerDurations} (in a single transaction, or the transaction
     * already in progress). The durations of other trackers are not changed.
     *
     * @param trackerDurations the {@link StoredTrackerDuration}s
     * @throws SQLException thrown if the database cannot be updated, in which case nothing is changed
     */
    void replaceTrackers(final List<StoredTrackerDuration> trackerDurations) throws SQLException {
        final List<String> trackerNames = trackerDurations.stream().map(StoredTrackerDuration::name).distinct().toList();
        connection.inTransaction(() -> {
            connection.batch(DELETE, trackerNames, (statement, trackerName) -> statement.setString(1, trackerName));
            return connection.batch(INSERT, trackerDurations, (statement, trackerDuration) -> {
                statement.setString(1, trackerDuration.name());
                statement.setLong(2, trackerDuration.finishedAt());
                statement.setLong(3, trackerDuration.duration());
            });
        });
    }
}
