package com.jujin.freeway.ioc.lifecycle.internal;

import com.jujin.freeway.ioc.internal.EagerLoadProxy;
import com.jujin.freeway.ioc.internal.ServiceActivityTracker;
import com.jujin.freeway.ioc.internal.ServiceStatus;
import com.jujin.freeway.ioc.lifecycle.ObjectCreator;

/**
 * Invoked from a fabricated service delegate to get or realize (instantiate and
 * configure) the service implementation. This includes synchronization logic,
 * to prevent multiple threads from attempting to realize the same service at
 * the same time (a service should be realized only once). The additional
 * interfaces implemented by this class support eager loading of services (at
 * application startup), and orderly shutdown of proxies.
 */
public class LazyObjectCreator<
    T
> implements ObjectCreator<T>, EagerLoadProxy, Runnable {

    private final ServiceActivityTracker tracker;

    private ObjectCreator<T> creator;

    private volatile T object;

    private final String serviceId;

    public LazyObjectCreator(
        ServiceActivityTracker tracker,
        ObjectCreator<T> creator,
        String serviceId
    ) {
        this.tracker = tracker;
        this.creator = creator;
        this.serviceId = serviceId;
    }

    /**
     * Checks to see if the proxy has been shutdown, then invokes
     * {@link ObjectCreator#create()} if it has not already done so.
     *
     * @throws IllegalStateException
     *             if the registry has been shutdown
     */
    @Override
    public T create() {
        if (object == null) obtainObjectFromCreator();

        return object;
    }

    private synchronized void obtainObjectFromCreator() {
        if (object != null) return;

        try {
            object = creator.create();

            // And if that's successful ...

            tracker.setStatus(serviceId, ServiceStatus.REAL);

            creator = null;
        } catch (RuntimeException ex) {
            throw new RuntimeException(
                String.format(
                    "Exception constructing service '%s': %s",
                    serviceId,
                    ex.getMessage()
                ),
                ex
            );
        }
    }

    /**
     * Invokes {@link #create()} to force the creation of the underlying
     * service.
     */
    @Override
    public void eagerLoad() {
        // Force object creation now

        create();
    }

    /**
     * Invoked when the Registry is shutdown; deletes the instantiated object (if it
     * exists) and replaces the ObjectCreator with one that throws an
     * IllegalStateException.
     */
    @Override
    public synchronized void run() {
        creator = () -> {
            throw new IllegalStateException(
                String.format(
                    "Proxy for service %s is no longer active because the IOC Registry has been shut down.",
                    serviceId
                )
            );
        };

        object = null;
    }
}
