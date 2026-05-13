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
 * <p>
 * A {@code Query} object is single-use: once executed, it is consumed and
 * calling any execute method again may produce undefined results.
 */
public interface Query {

    /** Binds a named parameter. */
    Query param(String name, Object value);

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
