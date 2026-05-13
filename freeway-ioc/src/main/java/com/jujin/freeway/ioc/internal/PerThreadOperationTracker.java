package com.jujin.freeway.ioc.internal;

import com.jujin.freeway.ioc.advisor.OperationTracker;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.function.Supplier;

/**
 * Manages a per-thread OperationTracker using ScopedValue (JDK 25).
 */
public class PerThreadOperationTracker implements OperationTracker {
    private final Logger logger;

    private static final ScopedValue<OperationTrackerImpl> TRACKER = ScopedValue.newInstance();

    public PerThreadOperationTracker(Logger logger) {
        this.logger = logger;
    }

    @Override
    public void run(String description, Runnable operation) {
        if (TRACKER.isBound()) {
            TRACKER.get().run(description, operation);
        } else {
            var impl = new OperationTrackerImpl(logger);
            ScopedValue.where(TRACKER, impl).run(() -> impl.run(description, operation));
        }
    }

    @Override
    public <T> T invoke(String description, Supplier<T> operation) {
        if (TRACKER.isBound()) {
            return TRACKER.get().invoke(description, operation);
        } else {
            var impl = new OperationTrackerImpl(logger);
            return ScopedValue.where(TRACKER, impl).call(() -> impl.invoke(description, operation));
        }
    }

    @Override
    public <T> T perform(String description, IOOperation<T> operation) throws IOException {
        if (TRACKER.isBound()) {
            return TRACKER.get().perform(description, operation);
        } else {
            var impl = new OperationTrackerImpl(logger);
            return ScopedValue.where(TRACKER, impl).call(() -> impl.perform(description, operation));
        }
    }
}
