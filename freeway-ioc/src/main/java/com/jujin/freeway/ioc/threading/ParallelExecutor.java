package com.jujin.freeway.ioc.threading;
import com.jujin.freeway.ioc.*;

import java.util.concurrent.Future;
import java.util.function.Supplier;

/**
 * A service that allows work to occur in parallel using a thread pool. The
 * thread pool is started lazily, and is shutdown when the Registry is shutdown.
 *
 * @see com.jujin.freeway.ioc.IOCSymbols
 */
public interface ParallelExecutor {
    /**
     * Submits the invocable object to be executed in a pooled thread. Returns a
     * Future object representing the eventual result of the invocable's operation.
     * The actual operation will be wrapped such that
     * {@link PerthreadManager#cleanup()} is invoked after the operation completes.
     *
     * @param invocable
     *            to execute in a thread
     * @param <T>
     * @return Future result of that invocation
     */
    <T> Future<T> invoke(Supplier<T> invocable);

    /**
     * As with {@link #invoke(java.util.function.Supplier)}, but the result is
     * wrapped inside a {@linkplain com.jujin.freeway.ioc.ThunkCreator thunk}.
     * Invoking methods on the thunk will block until the value is available.
     *
     * @param proxyType
     *            return type, used to create the thunk
     * @param invocable
     *            object that will eventually execute and return a value
     * @param <T>
     * @return the thunk
     */
    <T> T invoke(Class<T> proxyType, Supplier<T> invocable);
}
