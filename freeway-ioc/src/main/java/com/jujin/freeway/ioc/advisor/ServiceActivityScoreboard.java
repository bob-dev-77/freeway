package com.jujin.freeway.ioc.advisor;

import java.util.List;

/**
 * Provides access to the runtime details about services in the
 * {@link com.jujin.freeway.ioc.Registry}.
 */
public interface ServiceActivityScoreboard {
    /**
     * Returns the status of all services, sorted alphabetically by service id.
     */
    List<ServiceActivity> getServiceActivity();
}
