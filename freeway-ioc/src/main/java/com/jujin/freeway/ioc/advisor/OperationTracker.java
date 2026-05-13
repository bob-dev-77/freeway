package com.jujin.freeway.ioc.advisor;

import com.jujin.freeway.ioc.internal.IOOperation;

import java.io.IOException;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.function.Supplier;

/**
 * Used to track some set of operations in such a way that a failure (a thrown
 * RuntimeException) will be logged along with a trace of the stack of
 * operations.
 */
public interface OperationTracker {
    /**
     * Executes the operation. If the operation throws a {@link RuntimeException} it
     * will be logged and rethrown wrapped as a
     * {@link com.jujin.freeway.ioc.internal.OperationException}.
     *
     * @param description
     *            used if there is an exception
     * @param operation
     *            to execute
     */
    void run(String description, Runnable operation);

    /**
     * As with {@link #run(String, Runnable)}, but the operation may return a value.
     *
     * @param description
     *            used if there is an exception
     * @param operation
     *            to invoke
     * @return result of operation
     */
    <T> T invoke(String description, Supplier<T> operation);

    /**
     * As with {@link #invoke(String, Supplier)}, but the operation may throw an
     * {@link java.io.IOException}.
     *
     * @param description
     *            used if there is an exception (outside of IOException)
     * @param operation
     *            to perform
     * @return result of operation
     */
    <T> T perform(String description, IOOperation<T> operation) throws IOException;

    /**
     * Annotation to be be used in exception classes whose instances are not meant
     * to be logged in {@linkplain OperationTracker}.
     *
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    public static @interface NonLoggableException {
    }

}
