package com.jujin.freeway.db;

import java.util.function.Consumer;

/**
 * A database handle that doubles as a connection pool — no separate
 * {@code DataSource} or pool library needed.
 *
 * <p>
 * Usage:
 * <pre>{@code
 * Database db = new DatabaseBuilder()
 *     .url("jdbc:postgresql://localhost/mydb")
 *     .username("app").password("...")
 *     .build();
 *
 * List<User> users = db.sql("SELECT id, name FROM users WHERE status = ?", "active")
 *     .list(User.class);
 *
 * db.transaction(tx -> {
 *     tx.sql("UPDATE accounts SET balance = balance - ? WHERE id = ?", 100, 1).execute();
 *     tx.sql("UPDATE accounts SET balance = balance + ? WHERE id = ?", 100, 2).execute();
 * });
 * }</pre>
 *
 * <p>
 * {@code Database} is thread-safe and designed to be shared as a singleton.
 * Call {@link #close()} to shut down the pool when the application stops.
 */
public interface Database extends AutoCloseable {

    /**
     * Creates a parameterised query from a SQL string. Use {@code ?} for
     * positional parameters (bound in order from the varargs), or
     * {@code #name} / {@code :name} for named parameters (bound via
     * {@link Query#param(String, Object)} in any order).
     *
     * <pre>{@code
     * // positional
     * db.sql("SELECT * FROM t WHERE a = ? AND b = ?", 1, 2);
     *
     * // named — #name style (recommended, won't clash with SQL :: cast)
     * db.sql("INSERT INTO t (id, name, age) VALUES (#id, #name, #age)")
     *   .param("age", 35).param("name", "bob").param("id", 100001);
     *
     * // named — :name style (also supported)
     * db.sql("SELECT * FROM t WHERE name = :name").param("name", "Bob");
     * }</pre>
     */
    Query sql(String sql, Object... params);

    /**
     * Creates a batched statement for efficient multi-row inserts, updates, or
     * deletes. Supports both positional ({@code ?}) and named
     * ({@code #name}/{@code :name}) parameters.
     *
     * <pre>{@code
     * db.batch("INSERT INTO t (a, b) VALUES (?, ?)")
     *   .params(new Object[]{1, "x"}, new Object[]{2, "y"})
     *   .execute();
     * }</pre>
     */
    BatchQuery batch(String sql);

    /**
     * Executes {@code work} within a transaction. The transaction commits when
     * {@code work} returns normally and rolls back if it throws.
     */
    void transaction(Consumer<Transaction> work);

    /**
     * Opens a manual transaction. The caller must call {@link Transaction#commit()}
     * or {@link Transaction#rollback()}. The transaction auto-rolls back on
     * close if not committed.
     *
     * <pre>{@code
     * try (var tx = db.beginTransaction()) {
     *     tx.sql("...").execute();
     *     tx.commit();
     * }
     * }</pre>
     */
    Transaction beginTransaction();

    /** Returns true if the database is reachable. */
    boolean ping();

    /** Current pool statistics. */
    DatabaseStats stats();

    /** Shuts down the connection pool, closing all connections. */
    void close();
}
