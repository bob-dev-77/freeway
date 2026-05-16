package com.jujin.freeway.ioc.internal;

import com.jujin.freeway.ioc.ServiceDefinition;
import com.jujin.freeway.ioc.lifecycle.ObjectCreator;
import org.slf4j.Logger;

/**
 * Decorator for {@link com.jujin.freeway.ioc.lifecycle.ObjectCreator} that
 * ensures the service is only created once. This detects a situation where the
 * service builder for a service directly or indirectly invokes methods on the
 * service itself. This would show up as a second call up the ServiceCreator
 * stack injected into the proxy, potentially leading to endless recursion. We
 * try to identify that recursion and produce a useable exception report.
 */
public class ReentrantGuard implements ObjectCreator<Object> {

    private final ServiceDefinition serviceDef;

    private final ObjectCreator<?> delegate;

    private final Logger logger;

    private boolean locked;

    public ReentrantGuard(
        ServiceDefinition serviceDef,
        ObjectCreator<?> delegate,
        Logger logger) {
        this.serviceDef = serviceDef;
        this.delegate = delegate;
        this.logger = logger;
    }

    /**
     * We could make this method synchronized, but in the context of creating a
     * service for a proxy, it will already be synchronized (inside the proxy).
     */
    @Override
    public Object create() {
        if (locked)
            throw new IllegalStateException(
                String.format(
                    "Construction of service '%s' has failed due to recursion: the service depends on itself in some way. Please check %s for references to another service that is itself dependent on service '%1$s'.",
                    serviceDef.getServiceId(),
                    serviceDef.toString()));

        // Set the lock, to ensure that recursive service construction fails.

        locked = true;

        try {
            return delegate.create();
        } catch (RuntimeException ex) {
            logger.error(
                "Construction of service {} failed: {}",
                serviceDef.getServiceId(),
                ex.getMessage(),
                ex);

            // Release the lock on failure; the service is now in an unknown state, but we
            // may
            // be able to continue from here.

            locked = false;

            throw ex;
        }
    }
}
