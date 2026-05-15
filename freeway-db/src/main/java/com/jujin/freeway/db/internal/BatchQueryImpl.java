package com.jujin.freeway.db.internal;

import com.jujin.freeway.db.BatchQuery;
import com.jujin.freeway.db.SqlException;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

class BatchQueryImpl implements BatchQuery {

    private final DatabaseImpl db;
    private final PooledConnection transactionConnection;
    private final String sql;
    private final NamedParamParser.Result parsed;
    private List<Object[]> positionalRows;
    private List<Map<String, Object>> namedRows;

    BatchQueryImpl(DatabaseImpl db, PooledConnection transactionConnection, String sql) {
        this.db = db;
        this.transactionConnection = transactionConnection;
        this.sql = sql;
        this.parsed = NamedParamParser.parse(sql);
        this.positionalRows = new ArrayList<>();
    }

    @Override
    public BatchQuery params(Object[]... rows) {
        this.positionalRows = List.of(rows);
        return this;
    }

    @Override
    public BatchQuery paramsList(List<Object[]> rows) {
        this.positionalRows = rows;
        return this;
    }

    @Override
    public BatchQuery paramList(List<Map<String, Object>> rows) {
        this.namedRows = rows;
        return this;
    }

    @Override
    public int[] execute() {
        boolean ownConnection = transactionConnection == null;
        var conn = ownConnection ? db.pool.borrow() : transactionConnection;
        try {
            var stmt = conn.jdbcConnection()
                .prepareStatement(parsed.jdbcSql(), Statement.NO_GENERATED_KEYS);
            stmt.setQueryTimeout(db.queryTimeoutSeconds);
            try {
                if (namedRows != null && !namedRows.isEmpty()) {
                    for (var row : namedRows) {
                        bindRow(stmt, row);
                        stmt.addBatch();
                    }
                } else {
                    for (var row : positionalRows) {
                        for (int i = 0; i < row.length; i++) {
                            stmt.setObject(i + 1, row[i]);
                        }
                        stmt.addBatch();
                    }
                }
                return stmt.executeBatch();
            } finally {
                stmt.close();
            }
        } catch (SQLException e) {
            throw new SqlException("Batch execution failed: " + e.getMessage(), e);
        } finally {
            if (ownConnection) db.pool.release(conn);
        }
    }

    private void bindRow(PreparedStatement stmt, Map<String, Object> row)
        throws SQLException {
        // Validate that all named parameters have corresponding values
        for (String name : parsed.names()) {
            if (!row.containsKey(name)) {
                throw new SqlException(
                    "Missing value for named parameter '" + name +
                        "' in batch SQL: " + sql
                );
            }
        }
        // Check for extra parameters
        for (String paramName : row.keySet()) {
            if (!parsed.names().contains(paramName)) {
                throw new SqlException(
                    "Unknown named parameter '" + paramName +
                        "' in batch SQL: " + sql
                );
            }
        }
        // Bind parameters
        for (int i = 0; i < parsed.names().size(); i++) {
            String name = parsed.names().get(i);
            Object value = row.get(name);
            stmt.setObject(i + 1, value);
        }
    }
}
