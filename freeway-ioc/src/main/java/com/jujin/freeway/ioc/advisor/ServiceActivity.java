package com.jujin.freeway.ioc.advisor;

import com.jujin.freeway.ioc.internal.ServiceStatus;

import java.util.Set;

/**
 * Provided by the {@link ServiceActivityScoreboard} to track a single service's
 * state and activity.
 *
 * @see ServiceDefinition
 */
public interface ServiceActivity {
    /**
     * The unique id for the service.
     */
    String getServiceId();

    /**
     * The interface implemented by the service (this may occasionally be a class,
     * for non-proxied services).
     */
    Class<?> getServiceInterface();

    /**
     * The scope of the service (typically "singleton" or "perthread").
     */
    String getScope();

    /**
     * Indicates the lifecycle status of the service.
     */
    ServiceStatus getStatus();

    /**
     * The markers on this service
     */
    Set<Class<?>> getMarkers();
}
