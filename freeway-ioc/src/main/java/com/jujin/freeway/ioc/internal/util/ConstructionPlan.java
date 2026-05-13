package com.jujin.freeway.ioc.internal.util;

import com.jujin.freeway.ioc.config.*;
import com.jujin.freeway.ioc.property.*;
import com.jujin.freeway.ioc.threading.*;
import com.jujin.freeway.ioc.classpath.*;
import com.jujin.freeway.ioc.exception.*;
import com.jujin.freeway.ioc.lifecycle.ObjectCreator;
import com.jujin.freeway.ioc.config.*;
import com.jujin.freeway.ioc.property.*;
import com.jujin.freeway.ioc.threading.*;
import com.jujin.freeway.ioc.classpath.*;
import com.jujin.freeway.ioc.exception.*;
import com.jujin.freeway.ioc.advisor.OperationTracker;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Encapsulates the initial construction of an object instance, followed by a
 * series {@link InitializationPlan}s to initialize fields and invoke other
 * methods of the constructed object.
 *
 */
public class ConstructionPlan<T> implements ObjectCreator<T> {

    private final OperationTracker tracker;

    private final String description;

    private final Supplier<T> instanceConstructor;

    private List<InitializationPlan<T>> initializationPlans;

    public ConstructionPlan(
        OperationTracker tracker,
        String description,
        Supplier<T> instanceConstructor) {
        this.tracker = tracker;
        this.description = description;
        this.instanceConstructor = instanceConstructor;
    }

    @SuppressWarnings("unchecked")
    public ConstructionPlan<T> add(InitializationPlan<?> plan) {
        if (initializationPlans == null) {
            initializationPlans = new ArrayList<>();
        }

        initializationPlans.add((InitializationPlan<T>) plan);

        return this;
    }

    @Override
    public T create() {
        T result = tracker.invoke(description, instanceConstructor);

        if (initializationPlans != null) {
            executeInitializationPLans(result);
        }

        return result;
    }

    private void executeInitializationPLans(final T newInstance) {
        for (final InitializationPlan<T> plan : initializationPlans) {
            tracker.run(plan.getDescription(), () -> plan.initialize(newInstance));
        }
    }
}
