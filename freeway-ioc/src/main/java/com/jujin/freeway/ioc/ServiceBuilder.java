package com.jujin.freeway.ioc;

/**
 * A callback used to create a service implementation.
 */
public interface ServiceBuilder<T> {
    /**
     * Construct the service. A non-null object that implements the service
     * interface must be returned.
     *
     * @param resources
     *            used to lookup dependencies or access resources
     * @return the core service implementation
     */
    T buildService(ServiceContext resources);
}
