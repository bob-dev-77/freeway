package com.jujin.freeway.ioc.internal;

/**
 * Defines the three types of configurations a service may request.
 */
public enum ConfigurationType {

    /**
     * @see com.jujin.freeway.ioc.Configuration
     */
    UNORDERED,
    /**
     * @see com.jujin.freeway.ioc.OrderedConfiguration
     */
    ORDERED,
    /**
     * @see com.jujin.freeway.ioc.MappedConfiguration
     */
    MAPPED
}
