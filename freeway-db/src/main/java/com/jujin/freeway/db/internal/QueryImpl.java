package com.jujin.freeway.db.internal;

import com.jujin.freeway.db.Query;
import com.jujin.freeway.db.SqlException;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * {@link Query} implementation. Supports both positional ({@code ?}) and
 * named ({@code :name}) parameters.
 *
 * <p>
 * If {@code transactionConnection} is non-null, queries execute on that
 * connection. Otherwise, a connection is borrowed from the pool and released
 * after the query completes.
 */
class QueryImpl implements Query {

    private final DatabaseImpl db;
    private final PooledConnection transactionConnection;
    private final String originalSql;
    private final Object[] positionalParams;
    private final Map<String, Object> namedParams;
    private NamedParamParser.Result parsed;

    QueryImpl(DatabaseImpl db,
              PooledConnection transactionConnection,
              String sql,
              Object[] positionalParams) {
        this.db = db;
        this.transactionConnection = transactionConnection;
        this.originalSql = sql;
        this.positionalParams = positionalParams;
        this.namedParams = new HashMap<>();
    }

    @Override
    public Query param(String name, Object value) {
        namedParams.put(name, value);
        return this;
    }

    // ── Execution ───────────────────────────────────────────────────

    @Override
    public <T> List<T> list(Class<T> targetType) {
        try (var ctx = borrow()) {
            bindAll(ctx.stmt);
            try (var rs = ctx.stmt.executeQuery()) {
                var mapper = db.rowMapper.forClass(targetType);
                var list = new ArrayList<T>();
                int rowNum = 0;
                while (rs.next()) {
                    list.add(mapper.map(rs, rowNum++));
                }
                return list;
            }
        } catch (SQLException e) {
            throw new SqlException("Query failed: " + e.getMessage(), e);
        }
    }

    @Override
    public <T> Optional<T> one(Class<T> targetType) {
        try (var ctx = borrow()) {
            bindAll(ctx.stmt);
            try (var rs = ctx.stmt.executeQuery()) {
                if (rs.next()) {
                    var mapper = db.rowMapper.forClass(targetType);
                    return Optional.ofNullable(mapper.map(rs, 0));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new SqlException("Query failed: " + e.getMessage(), e);
        }
    }

    @Override
    public int execute() {
        try (var ctx = borrow()) {
            bindAll(ctx.stmt);
            return ctx.stmt.executeUpdate();
        } catch (SQLException e) {
            throw new SqlException("Update failed: " + e.getMessage(), e);
        }
    }

    @Override
    public <T> Stream<T> stream(Class<T> targetType) {
        // stream() needs the ResultSet to stay open, so we use a different pattern
        try {
            var ctx = borrow();
            bindAll(ctx.stmt);
            var rs = ctx.stmt.executeQuery();
            var mapper = db.rowMapper.forClass(targetType);

            var iterator = new java.util.Iterator<T>() {
                private boolean hasNext = rs.next();
                private int rowNum = 0;

                @Override
                public boolean hasNext() {
                    if (!hasNext) {
                        closeAll(rs, ctx.stmt, ctx.connectionSource());
                        return false;
                    }
                    return true;
                }

                @Override
                public T next() {
                    try {
                        T result = mapper.map(rs, rowNum++);
                        hasNext = rs.next();
                        if (!hasNext) closeAll(rs, ctx.stmt, ctx.connectionSource());
                        return result;
                    } catch (SQLException e) {
                        closeAll(rs, ctx.stmt, ctx.connectionSource());
                        throw new SqlException("Stream read failed", e);
                    }
                }
            };

            var spliterator = Spliterators.spliteratorUnknownSize(iterator, 0);
            return StreamSupport.stream(spliterator, false).onClose(() ->
                closeAll(rs, ctx.stmt, ctx.connectionSource()));
        } catch (SQLException e) {
            throw new SqlException("Stream query failed: " + e.getMessage(), e);
        }
    }

    // ── Parameter binding ───────────────────────────────────────────

    private void bindAll(PreparedStatement stmt) throws SQLException {
        if (!namedParams.isEmpty()) {
            bindNamed(stmt);
        } else {
            bindPositional(stmt);
        }
    }

    private void bindPositional(PreparedStatement stmt) throws SQLException {
        for (int i = 0; i < positionalParams.length; i++) {
            stmt.setObject(i + 1, positionalParams[i]);
        }
    }

    private void bindNamed(PreparedStatement stmt) throws SQLException {
        if (parsed == null) {
            parsed = NamedParamParser.parse(originalSql);
        }
        for (int i = 0; i < parsed.names().size(); i++) {
            String name = parsed.names().get(i);
            Object value = namedParams.get(name);
            stmt.setObject(i + 1, value);
        }
    }

    /** Returns the SQL to send to JDBC (with ? placeholders for named params). */
    private String jdbcSql() {
        if (!namedParams.isEmpty()) {
            if (parsed == null) parsed = NamedParamParser.parse(originalSql);
            return parsed.jdbcSql();
        }
        return originalSql;
    }

    // ── Connection management ───────────────────────────────────────

    /** Borrows from the pool or uses the transaction connection. */
    private ExecuteContext borrow() throws SQLException {
        if (transactionConnection != null) {
            var stmt = transactionConnection.jdbcConnection()
                .prepareStatement(jdbcSql(), Statement.NO_GENERATED_KEYS);
            return new ExecuteContext(stmt, null, null);
        } else {
            var conn = db.pool.borrow();
            var stmt = conn.jdbcConnection()
                .prepareStatement(jdbcSql(), Statement.NO_GENERATED_KEYS);
            return new ExecuteContext(stmt, conn, db.pool);
        }
    }

    private void closeAll(ResultSet rs, PreparedStatement stmt, PooledConnection conn) {
        try { rs.close(); } catch (SQLException ignored) {}
        try { stmt.close(); } catch (SQLException ignored) {}
        if (conn != null) db.pool.release(conn);
    }

    /** Holds a prepared statement, optional pool connection, and the pool for release. */
    private record ExecuteContext(PreparedStatement stmt, PooledConnection connectionSource,
                                   ConnectionPool pool) implements AutoCloseable {
        @Override
        public void close() {
            try { stmt.close(); } catch (SQLException ignored) {}
            if (connectionSource != null && pool != null) {
                pool.release(connectionSource);
            }
        }
    }
}
