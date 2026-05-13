package com.jujin.freeway.ioc.internal;

import java.io.IOException;

/**
 * An operation that, when performed, returns a value (like
 * {@link java.util.function.Supplier}, but may throw an
 * {@link java.io.IOException}.
 *
 * @see OperationTracker#perform(String, IOOperation)
 */
@FunctionalInterface
public interface IOOperation<T> {
    /**
     * Perform an operation and return a value, or throw the exception.
     */
    T perform() throws IOException;
}
