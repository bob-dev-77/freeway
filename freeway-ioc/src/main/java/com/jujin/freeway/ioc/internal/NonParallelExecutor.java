package com.jujin.freeway.ioc.internal;

import com.jujin.freeway.ioc.threading.ParallelExecutor;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

/**
 * Implementation of {@link ParallelExecutor} used when
 * {@linkplain com.jujin.freeway.ioc.IOCSymbols#THREAD_POOL_ENABLED the thread
 * pool is disabled}.
 *
 */
public class NonParallelExecutor implements ParallelExecutor {
    @Override
    public <T> Future<T> invoke(Supplier<T> invocable) {
        final T result = invocable.get();

        return new Future<T>() {
            @Override
            public boolean cancel(boolean mayInterruptIfRunning) {
                return false;
            }

            @Override
            public boolean isCancelled() {
                return false;
            }

            @Override
            public boolean isDone() {
                return true;
            }

            @Override
            public T get() throws InterruptedException, ExecutionException {
                return result;
            }

            @Override
            public T get(long timeout, TimeUnit unit)
                throws InterruptedException, ExecutionException, TimeoutException {
                return result;
            }
        };
    }

    @Override
    public <T> T invoke(Class<T> proxyType, Supplier<T> invocable) {
        return invocable.get();
    }
}
