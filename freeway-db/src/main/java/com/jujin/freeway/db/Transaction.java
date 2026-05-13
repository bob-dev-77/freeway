package com.jujin.freeway.db;

/**
 * A transactional scope obtained from {@link Database#beginTransaction()} or
 * the lambda-based {@link Database#transaction(Database.TransactionWork)}.
 *
 * <p>
 * All queries issued through a transaction share the same underlying JDBC
 * connection (held via {@code ScopedValue}). The transaction commits on
 * {@link #commit()} and rolls back on {@link #rollback()}, on exception
 * escape, or when closed without a prior commit.
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
