package com.jujin.freeway.ioc.internal;

import com.jujin.freeway.ioc.lifecycle.ObjectCreator;
import com.jujin.freeway.ioc.ServiceBuilderResources;
import com.jujin.freeway.ioc.internal.util.InternalUtils;
import java.lang.reflect.Constructor;
import org.slf4j.Logger;

/**
 * A service creator based on an implementation class' constructor, rather than
 * a service builder method.
 */
public class ConstructorServiceCreator implements ObjectCreator<Object> {

    private final Constructor<?> constructor;
    private final String creatorDescription;
    final ServiceBuilderResources resources;
    final Logger logger;
    final String serviceId;
    private final InjectionResourcesBuilder irBuilder;

    public ConstructorServiceCreator(
        ServiceBuilderResources resources,
        String creatorDescription,
        Constructor<?> constructor) {
        this.resources = resources;
        this.creatorDescription = creatorDescription;
        this.logger = resources.getLogger();
        this.serviceId = resources.getServiceId();
        this.constructor = constructor;
        this.irBuilder = new InjectionResourcesBuilder(
            resources,
            creatorDescription);
    }

    @Override
    public String toString() {
        return creatorDescription;
    }

    private ObjectCreator<?> plan;

    private ObjectCreator<?> getPlan() {
        if (plan == null) {
            String description = String.format(
                "Invoking constructor %s (for service '%s')",
                creatorDescription,
                resources.getServiceId());

            plan = InternalUtils.createConstructorConstructionPlan(
                resources.getTracker(),
                resources,
                irBuilder.build(),
                logger,
                description,
                constructor);
        }

        return plan;
    }

    @Override
    public Object create() {
        return getPlan().create();
    }
}
