package com.jujin.freeway.ioc.lifecycle;

import com.jujin.freeway.ioc.ServiceResources;

public interface ServiceLifecycle {
    /**
     * Returns the same creator, or a new one, that encapsulates the creation of the
     * core service implementation.
     *
     * @param resources
     *            source of information about the service to be created, and source
     *            of additional services or other resources that may be needed when
     *            constructing the core service implementation
     * @param creator
     *            object capable of creating the service implementation on demand.
     *            This is a wrapper around the service's builder method.
     * @return the service or equivalent service proxy
     */
    Object createService(ServiceResources resources, ObjectCreator<?> creator);

    /**
     * Returns true if the lifecycle is a singleton (a service that will only be
     * created once). Return false if the underlying service instance may be created
     * multiple times (for example, the
     * {@link com.jujin.freeway.ioc.internal.util.InternalUtils#PERTHREAD} scope}. A
     * future version of Freeway IoC may optimize for the later case.
     *
     * @return true for singletons, false for services that can be repeatedly
     *         constructed
     */
    boolean isSingleton();

    /**
     * If true, then lifecycle requires a proxy, meaning it is only usable with
     * services that properly define a service interface. The default (singleton)
     * scope does not require a proxy, but most other service scopes do.
     *
     * @return true if proxying is necessary, false otherwise
     */
    default boolean requiresProxy() {
        return true;
    }
}
