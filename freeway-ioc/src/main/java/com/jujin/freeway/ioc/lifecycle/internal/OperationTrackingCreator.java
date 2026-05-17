package com.jujin.freeway.ioc.lifecycle.internal;

import com.jujin.freeway.ioc.advisor.OperationTracker;
import com.jujin.freeway.ioc.lifecycle.ObjectCreator;
import java.util.function.Supplier;

/**
 * Makes sure the operations tracker is notified knows that a service is being
 * realized.
 */
public class OperationTrackingCreator implements ObjectCreator<Object> {

    private final OperationTracker tracker;

    private final String message;

    private final ObjectCreator<?> delegate;

    public OperationTrackingCreator(
        OperationTracker tracker,
        String message,
        ObjectCreator<?> delegate
    ) {
        this.tracker = tracker;
        this.message = message;
        this.delegate = delegate;
    }

    @Override
    public Object create() {
        Supplier<Object> operation = () -> delegate.create();

        return tracker.invoke(message, operation);
    }
}
