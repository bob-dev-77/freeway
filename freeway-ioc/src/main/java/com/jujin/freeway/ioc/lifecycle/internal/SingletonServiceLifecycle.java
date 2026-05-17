package com.jujin.freeway.ioc.lifecycle.internal;

import com.jujin.freeway.ioc.ServiceContext;
import com.jujin.freeway.ioc.lifecycle.ObjectCreator;
import com.jujin.freeway.ioc.lifecycle.ServiceLifecycle;

/**
 * The basic implementation of a service lifecycle, which simply uses the
 * {@link com.jujin.freeway.ioc.lifecycle.ObjectCreator} to create an instance
 * of the service when asked.
 */
public class SingletonServiceLifecycle implements ServiceLifecycle {

    @Override
    public Object createService(
        ServiceContext resources,
        ObjectCreator<?> creator
    ) {
        return creator.create();
    }

    @Override
    public boolean isSingleton() {
        return true;
    }

    @Override
    public boolean requiresProxy() {
        return false;
    }
}
