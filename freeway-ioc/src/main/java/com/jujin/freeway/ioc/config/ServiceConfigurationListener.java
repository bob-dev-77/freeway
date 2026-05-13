package com.jujin.freeway.ioc.config;

import com.jujin.freeway.ioc.ServiceDefinition;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Interface that defines listeners to services getting their distributed
 * configuration.
 */
@SuppressWarnings("rawtypes")
public interface ServiceConfigurationListener {
    /**
     * Receives a notification of an ordered configuraton being passed to a service.
     *
     * @param serviceDef
     *            a {@link ServiceDefinition} identifying the service receiving the
     *            configuration.
     * @param configuration
     *            a {@link List} containing the configuration itself.
     */
    void onOrderedConfiguration(ServiceDefinition serviceDef, List configuration);

    /**
     * Receives a notification of an unordered configuraton being passed to a
     * service.
     *
     * @param serviceDef
     *            a {@link ServiceDefinition} identifying the service receiving the
     *            configuration.
     * @param configuration
     *            a {@link Collection} containing the configuration itself.
     */
    void onUnorderedConfiguration(
        ServiceDefinition serviceDef,
        Collection configuration);

    /**
     * Receives a notification of a mapped configuraton being passed to a service.
     *
     * @param serviceDef
     *            a {@link ServiceDefinition} identifying the service receiving the
     *            configuration.
     * @param configuration
     *            a {@link Map} containing the configuration itself.
     */
    void onMappedConfiguration(ServiceDefinition serviceDef, Map configuration);
}
