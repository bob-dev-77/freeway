package com.jujin.freeway.ioc.internal;

import com.jujin.freeway.ioc.ServiceDefinition;
/**
 * Simple interface used when invoking a bind() method on a module class.
 *
 * @see com.jujin.freeway.ioc.internal.ServiceBinderImpl
 */
public interface ServiceDefAccumulator {
    void addServiceDef(ServiceDefinition serviceDef);
}
