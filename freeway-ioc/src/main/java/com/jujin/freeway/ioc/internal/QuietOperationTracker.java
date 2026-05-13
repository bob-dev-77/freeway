package com.jujin.freeway.ioc.internal;

import com.jujin.freeway.ioc.advisor.OperationTracker;

import java.io.IOException;
import java.util.function.Supplier;

/**
 * Minimal implementation used for testing, that does no logging, tracking, or
 * exception catching.
 */
public class QuietOperationTracker implements OperationTracker {
    @Override
    public void run(String description, Runnable operation) {
        operation.run();
    }

    @Override
    public <T> T invoke(String description, Supplier<T> operation) {
        return operation.get();
    }

    @Override
    public <T> T perform(String description, IOOperation<T> operation) throws IOException {
        return operation.perform();
    }
}
