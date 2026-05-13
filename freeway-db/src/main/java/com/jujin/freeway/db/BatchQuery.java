package com.jujin.freeway.db;

import java.util.List;
import java.util.Map;

/**
 * A batched SQL statement for efficient multi-row inserts, updates, or deletes.
 * Obtained from {@link Database#batch(String)}.
 *
 * <pre>{@code
 * // Positional parameters — one Object[] per row
 * int[] rows = db.batch("INSERT INTO t (a, b) VALUES (?, ?)")
 *     .params(new Object[]{1, "x"}, new Object[]{2, "y"})
 *     .execute();
 *
 * // Named parameters — one Map per row
 * int[] rows = db.batch("INSERT INTO t (a, b) VALUES (#a, #b)")
 *     .paramList(List.of(
 *         Map.of("a", 1, "b", "x"),
 *         Map.of("a", 2, "b", "y")))
 *     .execute();
 * }</pre>
 */
public interface BatchQuery {

    /** Positional parameters — one Object[] per row. */
    BatchQuery params(Object[]... rows);

    /** Positional parameters as a list. */
    BatchQuery paramsList(List<Object[]> rows);

    /** Named parameters — one Map per row. */
    BatchQuery paramList(List<Map<String, Object>> rows);

    /** Executes the batch and returns the number of affected rows per batch entry. */
    int[] execute();
}
