package com.jujin.freeway.ioc.internal;

import com.jujin.freeway.ioc.ServiceResources;
import com.jujin.freeway.ioc.lifecycle.ObjectCreator;
import com.jujin.freeway.ioc.lifecycle.ServiceLifecycle;

/**
 * Wrapper around a lifecycle, a set of resources for a service, and an
 * underlying {@link ObjectCreator} for a service that allows the service
 * lifecycle to alter the way that the service is created (this is needed for
 * the more advanced, non-singleton types of service lifecycles).
 */
public class LifecycleWrappedServiceCreator implements ObjectCreator<Object> {

    private final ServiceLifecycle lifecycle;

    private final ServiceResources resources;

    private final ObjectCreator<?> creator;

    public LifecycleWrappedServiceCreator(
        ServiceLifecycle lifecycle,
        ServiceResources resources,
        ObjectCreator<?> creator) {
        this.lifecycle = lifecycle;
        this.resources = resources;
        this.creator = creator;
    }

    /**
     * Passes the resources and the service creator through the
     * {@link com.jujin.freeway.ioc.lifecycle.ServiceLifecycle}.
     */
    @Override
    public Object create() {
        return lifecycle.createService(resources, creator);
    }
}
