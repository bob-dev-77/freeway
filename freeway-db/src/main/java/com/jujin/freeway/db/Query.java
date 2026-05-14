package com.jujin.freeway.db;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * A prepared SQL query with parameter binding and result mapping. Obtained from
 * {@link Database#sql(String, Object...)}.
 *
 * <p>
 * Positional ({@code ?}) parameters are bound in order from the varargs passed
 * to {@code sql()}. Named ({@code :name}) parameters are bound via
 * {@link #param(String, Object)}.
 *
 * <h3>Collection / array expansion</h3>
 * <p>
 * When a positional or named parameter value is a {@link java.util.Collection}
 * or array, the corresponding {@code ?} placeholder is <em>expanded</em> to
 * the matching number of placeholders:
 *
 * <pre>{@code
 * db.sql("SELECT * FROM users WHERE id IN (?)", List.of(1, 2, 3))
 *    .list(User.class);
 * // → WHERE id IN (?, ?, ?)
 *
 * db.sql("INSERT INTO t (name, email) VALUES (?)",
 *    List.of(new Object[]{"a", "a@x"}, new Object[]{"b", "b@x"}))
 *    .list(Long.class);
 * // → VALUES (?, ?), (?, ?)
 * }</pre>
 *
 * <h3>Fetch size</h3>
 * <p>
 * Use {@link #fetchSize(int)} to control JDBC fetch size for streaming
 * queries. Set to a positive value before calling {@link #stream(Class)}.
 *
 * <p>
 * A {@code Query} object is single-use: once executed, it is consumed and
 * calling any execute method again may produce undefined results.
 */
public interface Query {

    /** Binds a named parameter. */
    Query param(String name, Object value);

    /**
     * Sets the JDBC fetch size for {@link #stream(Class)}.  A positive value
     * controls how many rows are fetched per database round-trip.  Set to
     * {@code 0} (the default) to use the driver's default.
     */
    Query fetchSize(int rows);

    /** Executes the query and maps all rows to the given type. */
    <T> List<T> list(Class<T> targetType);

    /** Executes the query and maps at most one row. */
    <T> Optional<T> one(Class<T> targetType);

    /** Executes an INSERT, UPDATE, or DELETE and returns the number of affected rows. */
    int execute();

    /**
     * Executes the query and returns a lazy stream over the result rows. The
     * underlying {@code ResultSet} is closed when the stream is closed.
     */
    <T> Stream<T> stream(Class<T> targetType);
}
