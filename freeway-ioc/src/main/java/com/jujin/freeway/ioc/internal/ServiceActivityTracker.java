package com.jujin.freeway.ioc.internal;

import com.jujin.freeway.ioc.ServiceDefinition;
import com.jujin.freeway.ioc.config.*;
import com.jujin.freeway.ioc.property.*;
import com.jujin.freeway.ioc.threading.*;
import com.jujin.freeway.ioc.classpath.*;
import com.jujin.freeway.ioc.exception.*;
import com.jujin.freeway.ioc.internal.ServiceStatus;
import com.jujin.freeway.ioc.config.*;
import com.jujin.freeway.ioc.property.*;
import com.jujin.freeway.ioc.threading.*;
import com.jujin.freeway.ioc.classpath.*;
import com.jujin.freeway.ioc.exception.*;
import com.jujin.freeway.ioc.advisor.ServiceActivityScoreboard;

/**
 * Used to update the status of services defined by the
 * {@link ServiceActivityScoreboard}.
 */
public interface ServiceActivityTracker {
    /**
     * Defines a service in the tracker with an initial status.
     *
     * @param serviceDef
     *            the service being defined
     * @param status
     *            typically {@link ServiceStatus#BUILTIN} or {@link ServiceStatus#DEFINED}
     */
    void define(ServiceDefinition serviceDef, ServiceStatus status);

    /**
     * Updates the status for the service.
     *
     * @param serviceId
     *            identifies the service, which must be previously defined
     * @param status
     *            the new status value
     */
    void setStatus(String serviceId, ServiceStatus status);
}
