package com.jujin.freeway.ioc.internal.util;

/**
 * Performs one initialization step on a newly created object.
 *
 */
public interface InitializationPlan<T> {
    /**
     * The description of the operation, used with the
     * {@link com.jujin.freeway.ioc.advisor.OperationTracker}.
     *
     */
    String getDescription();

    /**
     * Invoked by the {@link ConstructionPlan} inside a
     * {@linkplain com.jujin.freeway.ioc.advisor.OperationTracker#run(String, Runnable)
     * operation tracker block}.
     */
    void initialize(T instance);
}
