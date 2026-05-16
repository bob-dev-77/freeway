package com.jujin.freeway.ioc.internal.util;

import com.jujin.freeway.ioc.advisor.OperationTracker;
import com.jujin.freeway.ioc.lifecycle.ObjectCreator;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Encapsulates the initial construction of an object instance, followed by a
 * series {@link InitializePlan}s to initialize fields and invoke other
 * methods of the constructed object.
 *
 */
public class InstancePlan<T> implements ObjectCreator<T> {

    private final OperationTracker tracker;

    private final String description;

    private final Supplier<T> instanceConstructor;

    private List<InitializePlan<T>> initializePlans;

    public InstancePlan(
        OperationTracker tracker,
        String description,
        Supplier<T> instanceConstructor
    ) {
        this.tracker = tracker;
        this.description = description;
        this.instanceConstructor = instanceConstructor;
    }

    @SuppressWarnings("unchecked")
    public InstancePlan<T> add(InitializePlan<?> plan) {
        if (initializePlans == null) {
            initializePlans = new ArrayList<>();
        }

        initializePlans.add((InitializePlan<T>) plan);

        return this;
    }

    @Override
    public T create() {
        T result = tracker.invoke(description, instanceConstructor);

        if (initializePlans != null) {
            executeInitializationPLans(result);
        }

        return result;
    }

    private void executeInitializationPLans(final T newInstance) {
        for (final InitializePlan<T> plan : initializePlans) {
            tracker.run(plan.description(), () ->
                    plan.initialize(newInstance)
            );
        }
    }
}
