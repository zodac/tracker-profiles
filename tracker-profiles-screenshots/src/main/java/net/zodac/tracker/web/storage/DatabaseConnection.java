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

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * The single connection to the SQLite database, shared by every repository. Each call holds the lock for the whole statement (or transaction),
 * since a JDBC {@link Connection} must not be used by several threads at once. SQLite allows a single writer, so a connection pool would not help.
 */
final class DatabaseConnection implements AutoCloseable {

    private static final ParameterBinder NO_PARAMETERS = _ -> {
        // No parameters to bind
    };

    private final Lock lock = new ReentrantLock();
    private final Connection connection;

    private DatabaseConnection(final Connection connection) {
        this.connection = connection;
    }

    /**
     * Opens the connection to the database.
     *
     * <p>
     * WAL with {@code NORMAL} synchronisation makes each (small, frequent) autosave from the web UI much cheaper than the default rollback
     * journal, and is still safe from corruption on a crash (only the last transactions may be lost on a power failure). WAL needs the database to
     * be on a local filesystem, not a network share.
     *
     * @param jdbcUrl the JDBC URL of the database
     * @return the {@link DatabaseConnection}
     * @throws SQLException thrown if the database cannot be opened
     */
    static DatabaseConnection open(final String jdbcUrl) throws SQLException {
        final Connection connection = DriverManager.getConnection(jdbcUrl);
        try (final Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA journal_mode = WAL");
            statement.execute("PRAGMA synchronous = NORMAL");
        } catch (final SQLException e) {
            connection.close();
            throw e;
        }
        return new DatabaseConnection(connection);
    }

    /**
     * Executes a query, mapping each row of the result.
     *
     * @param sql    the SQL query
     * @param binder binds the parameters of the query
     * @param mapper maps each row to a value
     * @param <T>    the type of each mapped row
     * @return the mapped rows, in the order returned by the query
     * @throws SQLException thrown if the query fails
     */
    <T> List<T> query(final String sql, final ParameterBinder binder, final RowMapper<T> mapper) throws SQLException {
        lock.lock();
        try (final PreparedStatement statement = connection.prepareStatement(sql)) {
            binder.bind(statement);
            try (final ResultSet resultSet = statement.executeQuery()) {
                final List<T> rows = new ArrayList<>();
                while (resultSet.next()) {
                    rows.add(mapper.map(resultSet));
                }
                return rows;
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Executes a query with no parameters, mapping each row of the result.
     *
     * @param sql    the SQL query
     * @param mapper maps each row to a value
     * @param <T>    the type of each mapped row
     * @return the mapped rows, in the order returned by the query
     * @throws SQLException thrown if the query fails
     */
    <T> List<T> query(final String sql, final RowMapper<T> mapper) throws SQLException {
        return query(sql, NO_PARAMETERS, mapper);
    }

    /**
     * Executes a query, returning whether it returns any row.
     *
     * @param sql    the SQL query
     * @param binder binds the parameters of the query
     * @return {@code true} if at least one row is returned
     * @throws SQLException thrown if the query fails
     */
    boolean exists(final String sql, final ParameterBinder binder) throws SQLException {
        lock.lock();
        try (final PreparedStatement statement = connection.prepareStatement(sql)) {
            binder.bind(statement);
            try (final ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Executes an {@code INSERT}, {@code UPDATE} or {@code DELETE} statement.
     *
     * @param sql    the SQL statement
     * @param binder binds the parameters of the statement
     * @return the number of rows changed
     * @throws SQLException thrown if the statement fails
     */
    int update(final String sql, final ParameterBinder binder) throws SQLException {
        lock.lock();
        try (final PreparedStatement statement = connection.prepareStatement(sql)) {
            binder.bind(statement);
            return statement.executeUpdate();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Executes an {@code INSERT}, {@code UPDATE} or {@code DELETE} statement with no parameters.
     *
     * @param sql the SQL statement
     * @return the number of rows changed
     * @throws SQLException thrown if the statement fails
     */
    int update(final String sql) throws SQLException {
        return update(sql, NO_PARAMETERS);
    }

    /**
     * Executes a statement once for each item, in a single transaction (or the transaction already in progress).
     *
     * @param sql    the SQL statement
     * @param items  the items to execute the statement for
     * @param binder binds the parameters of the statement for an item
     * @param <P>    the type of each item
     * @return the number of rows changed for each item, in the order of {@code items}
     * @throws SQLException thrown if any statement fails, in which case no item is changed
     */
    <P> int[] batch(final String sql, final List<P> items, final ItemBinder<P> binder) throws SQLException {
        return inTransaction(() -> {
            try (final PreparedStatement statement = connection.prepareStatement(sql)) {
                for (final P item : items) {
                    binder.bind(statement, item);
                    statement.addBatch();
                }
                return statement.executeBatch();
            }
        });
    }

    /**
     * Executes the {@code work} in a single transaction, committed if it completes and rolled back if it throws. If a transaction is already in
     * progress (on this thread), the {@code work} is part of it.
     *
     * @param work the statements to execute, using the other methods of this {@link DatabaseConnection}
     * @param <T>  the type of the result
     * @return the result of the {@code work}
     * @throws SQLException thrown if the {@code work} fails, in which case nothing is changed
     */
    <T> T inTransaction(final TransactionWork<T> work) throws SQLException {
        lock.lock();
        try {
            if (!connection.getAutoCommit()) {
                return work.execute();
            }

            connection.setAutoCommit(false);
            try {
                final T result = work.execute();
                connection.commit();
                return result;
            } catch (final SQLException | RuntimeException e) {
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
     * Lets SQLite update its query planner statistics (as recommended before closing a long-lived connection), then closes the connection.
     *
     * @throws SQLException thrown if the connection cannot be closed
     */
    @Override
    public void close() throws SQLException {
        lock.lock();
        try (connection; final Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA optimize");
        } finally {
            lock.unlock();
        }
    }

    /**
     * Binds the parameters of a {@link PreparedStatement}.
     */
    @FunctionalInterface
    interface ParameterBinder {

        /**
         * Binds the parameters.
         *
         * @param statement the {@link PreparedStatement}
         * @throws SQLException thrown if a parameter cannot be bound
         */
        void bind(PreparedStatement statement) throws SQLException;
    }

    /**
     * Binds the parameters of a {@link PreparedStatement} for one item of a batch.
     *
     * @param <P> the type of each item
     */
    @FunctionalInterface
    interface ItemBinder<P> {

        /**
         * Binds the parameters for the item.
         *
         * @param statement the {@link PreparedStatement}
         * @param item      the item
         * @throws SQLException thrown if a parameter cannot be bound
         */
        void bind(PreparedStatement statement, P item) throws SQLException;
    }

    /**
     * Maps the current row of a {@link ResultSet}.
     *
     * @param <T> the type of the mapped row
     */
    @FunctionalInterface
    interface RowMapper<T> {

        /**
         * Maps the current row.
         *
         * @param resultSet the {@link ResultSet}, positioned on the row
         * @return the mapped row
         * @throws SQLException thrown if a column cannot be read
         */
        T map(ResultSet resultSet) throws SQLException;
    }

    /**
     * Statements executed in a single transaction.
     *
     * @param <T> the type of the result
     */
    @FunctionalInterface
    interface TransactionWork<T> {

        /**
         * Executes the statements.
         *
         * @return the result
         * @throws SQLException thrown if a statement fails
         */
        T execute() throws SQLException;
    }
}
