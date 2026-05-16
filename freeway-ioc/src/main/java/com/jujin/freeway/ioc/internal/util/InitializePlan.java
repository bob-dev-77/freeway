package com.jujin.freeway.ioc.internal.util;

/**
 * Performs one initialization step on a newly created object.
 *
 */
public interface InitializePlan<T> {
    /**
     * The description of the operation, used with the
     * {@link com.jujin.freeway.ioc.advisor.OperationTracker}.
     *
     */
    String description();

    /**
     * Invoked by the {@link InstancePlan} inside a
     * {@linkplain com.jujin.freeway.ioc.advisor.OperationTracker#run(String, Runnable)
     * operation tracker block}.
     */
    void initialize(T instance);
}
