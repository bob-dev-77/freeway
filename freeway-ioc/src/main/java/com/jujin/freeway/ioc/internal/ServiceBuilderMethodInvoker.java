package com.jujin.freeway.ioc.internal;

import com.jujin.freeway.ioc.ServiceBuilderResources;
import com.jujin.freeway.ioc.internal.util.InternalUtils;
import com.jujin.freeway.ioc.lifecycle.ObjectCreator;
import org.slf4j.Logger;

import java.lang.reflect.Method;

/**
 * Basic implementation of {@link com.jujin.freeway.ioc.lifecycle.ObjectCreator}
 * that handles invoking a method on the module builder, and figures out the
 * correct parameters to pass into the annotated method.
 */
public class ServiceBuilderMethodInvoker implements ObjectCreator<Object> {

    private final Method builderMethod;
    private final String creatorDescription;
    final ServiceBuilderResources resources;
    final Logger logger;
    final String serviceId;
    private final InjectionResourcesBuilder irBuilder;

    public ServiceBuilderMethodInvoker(
        ServiceBuilderResources resources,
        String creatorDescription,
        Method method) {
        this.resources = resources;
        this.creatorDescription = creatorDescription;
        this.logger = resources.getLogger();
        this.serviceId = resources.getServiceId();
        builderMethod = method;
        this.irBuilder = new InjectionResourcesBuilder(
            resources,
            creatorDescription);
    }

    private ObjectCreator<Object> plan;

    private ObjectCreator<Object> getPlan() {
        if (plan == null) {
            // Defer getting (and possibly instantiating) the module instance until the last
            // possible
            // moment. If the method is static, there's no need to even get the builder.

            final Object moduleInstance = InternalUtils.isStatic(builderMethod)
                ? null
                : resources.getModuleBuilder();

            plan = InternalUtils.createMethodInvocationPlan(
                resources.getTracker(),
                resources,
                irBuilder.build(),
                logger,
                "Constructing service implementation via " + creatorDescription,
                moduleInstance,
                builderMethod);
        }

        return plan;
    }

    /**
     * Invoked from the proxy to create the actual service implementation.
     */
    @Override
    public Object create() {
        Object result = getPlan().create();

        if (result == null) {
            throw new RuntimeException(
                String.format(
                    "Builder method %s (for service '%s') returned null.",
                    creatorDescription,
                    serviceId));
        }

        return result;
    }

    @Override
    public String toString() {
        return creatorDescription;
    }
}
