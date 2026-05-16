package com.jujin.freeway.ioc.internal;

import com.jujin.freeway.ioc.advisor.ThunkCreator;
import com.jujin.freeway.ioc.lifecycle.ObjectCreator;
import com.jujin.freeway.ioc.lifecycle.PerThreadManager;
import com.jujin.freeway.ioc.threading.ParallelExecutor;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.function.Supplier;

public class ParallelExecutorImpl implements ParallelExecutor {

    private final ThunkCreator thunkCreator;

    private final ExecutorService executorService;

    private final PerThreadManager perthreadManager;

    public ParallelExecutorImpl(
        ExecutorService executorService,
        ThunkCreator thunkCreator,
        PerThreadManager perthreadManager
    ) {
        this.executorService = executorService;
        this.thunkCreator = thunkCreator;
        this.perthreadManager = perthreadManager;
    }

    @Override
    public <T> Future<T> invoke(Supplier<T> invocable) {
        assert invocable != null;

        return executorService.submit(toCallable(invocable));
    }

    private <T> Callable<T> toCallable(final Supplier<T> invocable) {
        return new Callable<T>() {
            @Override
            public T call() throws Exception {
                try {
                    return invocable.get();
                } finally {
                    perthreadManager.cleanup();
                }
            }
        };
    }

    @Override
    public <T> T invoke(Class<T> proxyType, Supplier<T> invocable) {
        final Future<T> future = invoke(invocable);

        ObjectCreator<T> creator = () -> {
            try {
                return future.get();
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        };

        String description = String.format(
            "FutureThunk[%s]",
            proxyType.getName()
        );

        return thunkCreator.createThunk(
            proxyType,
            new CachingObjectCreator<T>(creator),
            description
        );
    }
}
