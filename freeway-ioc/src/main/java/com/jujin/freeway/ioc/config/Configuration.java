package com.jujin.freeway.ioc.config;

/**
 * Object passed into a service contributor method that allows the method
 * provide contributed values to the service's configuration.
 * <p>
 * A service can <em>collect</em> contributions in three different ways:
 * <ul>
 * <li>As an un-ordered collection of values</li>
 * <li>As an ordered list of values (where each value has a unique id,
 * pre-requisites and post-requisites)</li>
 * <li>As a map of keys and values
 * </ul>
 * <p>
 * This implementation is used for un-ordered configuration data.
 * <p>
 * The service defines the <em>type</em> of contribution, in terms of a base
 * class or service interface. Contributions must be compatible with the type.
 */
public interface Configuration<T> {
    /**
     * Adds an object to the service's contribution.
     *
     * @param object
     *            to add to the service's configuration
     */
    void add(T object);

    /**
     * Automatically instantiates an instance of the class, with dependencies
     * injected, and adds it to the configuration. When the configuration type is an
     * interface and the class to be contributed is a local file, then a reloadable
     * proxy for the class will be created and contributed.
     *
     * @param clazz
     *            what class to instantiate
     */
    void addInstance(Class<? extends T> clazz);
}
