package com.jujin.freeway.db.internal;

import com.jujin.freeway.db.Query;
import com.jujin.freeway.db.SqlException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Spliterators;
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
    private int fetchSize;

    /** Expansion cache: built lazily before first borrow(). */
    private boolean expandedChecked;
    private String expandedSql;
    private Object[] expandedFlatParams;

    QueryImpl(
        DatabaseImpl db,
        PooledConnection transactionConnection,
        String sql,
        Object[] positionalParams
    ) {
        this.db = db;
        this.transactionConnection = transactionConnection;
        this.originalSql = sql;
        this.positionalParams = positionalParams;
        this.namedParams = new HashMap<>();
        this.fetchSize = 0;
    }

    @Override
    public Query param(String name, Object value) {
        namedParams.put(name, value);
        return this;
    }

    @Override
    public Query fetchSize(int rows) {
        if (rows < 0) throw new IllegalArgumentException(
            "fetchSize must be >= 0"
        );
        this.fetchSize = rows;
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
                        if (!hasNext) closeAll(
                            rs,
                            ctx.stmt,
                            ctx.connectionSource()
                        );
                        return result;
                    } catch (SQLException e) {
                        closeAll(rs, ctx.stmt, ctx.connectionSource());
                        throw new SqlException("Stream read failed", e);
                    }
                }
            };

            var spliterator = Spliterators.spliteratorUnknownSize(iterator, 0);
            return StreamSupport.stream(spliterator, false).onClose(() ->
                closeAll(rs, ctx.stmt, ctx.connectionSource())
            );
        } catch (SQLException e) {
            throw new SqlException("Stream query failed: " + e.getMessage(), e);
        }
    }

    // ── Parameter binding ───────────────────────────────────────────

    private void bindAll(PreparedStatement stmt) throws SQLException {
        ensureExpanded();
        if (expandedFlatParams != null) {
            for (int i = 0; i < expandedFlatParams.length; i++) {
                stmt.setObject(i + 1, expandedFlatParams[i]);
            }
            return;
        }
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
        ensureExpanded();
        if (expandedSql != null) return expandedSql;
        if (!namedParams.isEmpty()) {
            if (parsed == null) parsed = NamedParamParser.parse(originalSql);
            return parsed.jdbcSql();
        }
        return originalSql;
    }

    // ── Collection / array expansion ────────────────────────────────

    private void ensureExpanded() {
        if (expandedChecked) return;
        expandedChecked = true;
        if (!namedParams.isEmpty()) {
            expandNamed();
        } else {
            expandPositional();
        }
    }

    /** Expand positional {@code ?} placeholders when the value is a Collection or array. */
    private void expandPositional() {
        var sb = new StringBuilder();
        var flat = new ArrayList<>();
        int sqlIdx = 0;

        for (Object param : positionalParams) {
            int q = originalSql.indexOf('?', sqlIdx);
            if (q < 0) break;
            sb.append(originalSql, sqlIdx, q);
            sqlIdx = q + 1;

            if (param instanceof Collection<?> col) {
                if (col.isEmpty()) throw new SqlException(
                    "Cannot expand empty Collection for '?' placeholder"
                );
                appendExpanded(sb, flat, col);
            } else if (param instanceof Object[] arr) {
                appendExpanded(sb, flat, Arrays.asList(arr));
            } else {
                sb.append('?');
                flat.add(param);
            }
        }
        sb.append(originalSql, sqlIdx, originalSql.length());

        if (flat.size() != positionalParams.length) {
            expandedSql = sb.toString();
            expandedFlatParams = flat.toArray();
        }
    }

    /** Expand named parameters whose value is a Collection or array. */
    private void expandNamed() {
        if (parsed == null) parsed = NamedParamParser.parse(originalSql);

        String jdbcSql = parsed.jdbcSql();
        boolean anyExpanded = false;
        int qIdx = 0;

        var sqlOut = new StringBuilder();
        var flat = new ArrayList<>();

        for (String name : parsed.names()) {
            int q = jdbcSql.indexOf('?', qIdx);
            if (q < 0) break;
            sqlOut.append(jdbcSql, qIdx, q);
            qIdx = q + 1;

            Object value = namedParams.get(name);
            if (value instanceof Collection<?> col) {
                if (col.isEmpty()) throw new SqlException(
                    "Cannot expand empty Collection for named param '" +
                        name +
                        "'"
                );
                appendExpanded(sqlOut, flat, col);
                anyExpanded = true;
            } else if (value instanceof Object[] arr) {
                if (arr.length == 0) throw new SqlException(
                    "Cannot expand empty array for named param '" + name + "'"
                );
                appendExpanded(sqlOut, flat, Arrays.asList(arr));
                anyExpanded = true;
            } else {
                sqlOut.append('?');
                flat.add(value);
            }
        }
        sqlOut.append(jdbcSql, qIdx, jdbcSql.length());

        if (anyExpanded) {
            expandedSql = sqlOut.toString();
            expandedFlatParams = flat.toArray();
        }
    }

    /** Append expanded placeholders for a Collection. */
    private static void appendExpanded(
        StringBuilder sb,
        ArrayList<Object> flat,
        Collection<?> col
    ) {
        Iterator<?> it = col.iterator();
        Object first = it.next();
        if (first instanceof Object[] row) {
            // Row expansion: each element is a row → (?, ?), (?, ?)
            appendRow(sb, flat, row, 0);
            while (it.hasNext()) {
                sb.append(',');
                appendRow(sb, flat, (Object[]) it.next(), 0);
            }
        } else if (first instanceof Collection<?> inner) {
            appendRowCol(sb, flat, inner, 0);
            while (it.hasNext()) {
                sb.append(',');
                appendRowCol(sb, flat, (Collection<?>) it.next(), 0);
            }
        } else {
            // Simple IN expansion: ?, ?, ?
            sb.append('?');
            flat.add(first);
            while (it.hasNext()) {
                sb.append(",?");
                flat.add(it.next());
            }
        }
    }

    private static void appendRow(
        StringBuilder sb,
        ArrayList<Object> flat,
        Object[] row,
        int depth
    ) {
        sb.append('(');
        for (int i = 0; i < row.length; i++) {
            if (i > 0) sb.append(',');
            Object v = row[i];
            if (v instanceof Object[] nested) {
                appendRow(sb, flat, nested, depth + 1);
            } else if (v instanceof Collection<?> nc) {
                sb.append('?');
                flat.addAll(nc);
            } else {
                sb.append('?');
                flat.add(v);
            }
        }
        sb.append(')');
    }

    private static void appendRowCol(
        StringBuilder sb,
        ArrayList<Object> flat,
        Collection<?> col,
        int depth
    ) {
        sb.append('(');
        int i = 0;
        for (Object v : col) {
            if (i++ > 0) sb.append(',');
            if (v instanceof Object[] nested) {
                appendRow(sb, flat, nested, depth + 1);
            } else {
                sb.append('?');
                flat.add(v);
            }
        }
        sb.append(')');
    }

    // ── Connection management ───────────────────────────────────────

    /** Borrows from the pool or uses the transaction connection. */
    private ExecuteContext borrow() throws SQLException {
        if (transactionConnection != null) {
            var stmt = transactionConnection
                .jdbcConnection()
                .prepareStatement(jdbcSql(), Statement.NO_GENERATED_KEYS);
            stmt.setQueryTimeout(db.queryTimeoutSeconds);
            if (fetchSize > 0) stmt.setFetchSize(fetchSize);
            return new ExecuteContext(stmt, null, null);
        } else {
            var conn = db.pool.borrow();
            var stmt = conn
                .jdbcConnection()
                .prepareStatement(jdbcSql(), Statement.NO_GENERATED_KEYS);
            stmt.setQueryTimeout(db.queryTimeoutSeconds);
            if (fetchSize > 0) stmt.setFetchSize(fetchSize);
            return new ExecuteContext(stmt, conn, db.pool);
        }
    }

    private void closeAll(
        ResultSet rs,
        PreparedStatement stmt,
        PooledConnection conn
    ) {
        try {
            rs.close();
        } catch (SQLException ignored) {}
        try {
            stmt.close();
        } catch (SQLException ignored) {}
        if (conn != null && db.pool != null) db.pool.release(conn);
    }

    /** Holds a prepared statement, optional pool connection, and the pool for release. */
    private record ExecuteContext(
        PreparedStatement stmt,
        PooledConnection connectionSource,
        ConnectionPool pool
    ) implements AutoCloseable {
        @Override
        public void close() {
            try {
                stmt.close();
            } catch (SQLException ignored) {}
            if (connectionSource != null && pool != null) {
                pool.release(connectionSource);
            }
        }
    }
}
