package com.jujin.freeway.ioc.config;

import com.jujin.freeway.ioc.annotations.UsesOrderedConfiguration;

import java.util.Collections;
import java.util.List;

/**
 * Service that collects the {@link ServiceConfigurationListener}s. Don't use
 * this service directly.
 */
@UsesOrderedConfiguration(ServiceConfigurationListener.class)
final public class ServiceConfigurationListenerHub {

    final private List<ServiceConfigurationListener> listeners;

    public ServiceConfigurationListenerHub(List<ServiceConfigurationListener> listeners) {
        super();
        this.listeners = Collections.unmodifiableList(listeners);
    }

    /**
     * Returns the list of service configuration listeners.
     */
    public List<ServiceConfigurationListener> getListeners() {
        return listeners;
    }

}
