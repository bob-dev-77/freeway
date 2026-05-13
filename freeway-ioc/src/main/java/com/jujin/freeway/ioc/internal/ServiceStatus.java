package com.jujin.freeway.ioc.internal;

import com.jujin.freeway.ioc.Registry;

/**
 * Used in {@link ServiceActivity} to identify the state of the service in terms
 * of its overall lifecycle.
 */
public enum ServiceStatus {
    /**
     * A builtin service that exists before the {@link Registry} is constructed.
     */
    BUILTIN,

    /**
     * The service is defined in a module, but has not yet been referenced.
     */
    DEFINED,

    /**
     * A proxy has been created for the service, but no methods of the proxy have
     * been invoked.
     */
    VIRTUAL,

    /**
     * A service implementation for the service has been created.
     */
    REAL
}
