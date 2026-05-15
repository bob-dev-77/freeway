package com.jujin.freeway.db;

/**
 * A transactional scope obtained from {@link Database#beginTransaction()} or
 * the lambda-based {@link Database#transaction(java.util.function.Consumer)}.
 *
 * <p>
 * All queries issued through a transaction share the same underlying JDBC
 * connection. The transaction commits on {@link #commit()} and rolls back on
 * {@link #rollback()}, on exception escape, or when closed without a prior commit.
 *
 * <p>
 * <b>Usage pattern:</b>
 * <pre>{@code
 * // Lambda-style (preferred)
 * db.transaction(tx -> {
 *     tx.sql("UPDATE accounts SET balance = balance - ? WHERE id = ?", 100, 1).execute();
 *     tx.sql("UPDATE accounts SET balance = balance + ? WHERE id = ?", 100, 2).execute();
 * });
 *
 * // Manual transaction with try-with-resources
 * try (var tx = db.beginTransaction()) {
 *     tx.sql("...").execute();
 *     tx.commit();
 * }
 * }</pre>
 */
public interface Transaction extends AutoCloseable {

    /** Creates a query within this transaction's connection. */
    Query sql(String sql, Object... params);

    /** Creates a batched statement within this transaction's connection. */
    BatchQuery batch(String sql);

    /** Commits and releases the connection back to the pool. Idempotent. */
    void commit();

    /** Rolls back and releases the connection back to the pool. Idempotent. */
    void rollback();

    /**
     * Closes this transaction, rolling back if not already committed. This is
     * the escape hatch — prefer {@link #commit()} for the happy path.
     */
    void close();
}
