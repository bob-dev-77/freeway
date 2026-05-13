package com.jujin.freeway.ioc.internal;

import com.jujin.freeway.ioc.ServiceDefinition;
import com.jujin.freeway.ioc.config.*;
import com.jujin.freeway.ioc.property.*;
import com.jujin.freeway.ioc.threading.*;
import com.jujin.freeway.ioc.classpath.*;
import com.jujin.freeway.ioc.exception.*;
import com.jujin.freeway.ioc.advisor.AdvisorDefinition;
import com.jujin.freeway.ioc.config.*;
import com.jujin.freeway.ioc.property.*;
import com.jujin.freeway.ioc.threading.*;
import com.jujin.freeway.ioc.classpath.*;
import com.jujin.freeway.ioc.exception.*;
import com.jujin.freeway.ioc.config.ContributionDef;
import com.jujin.freeway.ioc.ModuleBuilderSource;
import java.util.Collection;
import java.util.Set;

/**
 * A module within the Freeway IoC registry. Each Module is constructed around a
 * corresponding module builder instance; the methods and annotations of that
 * instance define the services provided by the module.
 */
public interface Module extends ModuleBuilderSource {
    /**
     * Locates a service given a service id and the corresponding service interface
     * type.
     *
     * @param <T>
     * @param serviceId
     *            identifies the service to access
     * @param serviceInterface
     *            the interface the service implements
     * @return the service's proxy
     * @throws RuntimeException
     *             if there is an error instantiating the service proxy
     */
    <T> T getService(String serviceId, Class<T> serviceInterface);

    /**
     * Locates the ids of all services that implement the provided service
     * interface, or whose service interface is assignable to the provided service
     * interface (is a super-class or super-interface).
     *
     * @param serviceInterface
     *            the interface to search for
     * @return a collection of service ids
     */
    Collection<String> findServiceIdsForInterface(Class<?> serviceInterface);

    /**
     * Iterates over any advisor definitions defined by the module and returns those
     * that apply to the provided service definition.
     *
     * @param serviceDef
     *            for which advisors are being assembled
     * @return set of advisors, possibly empty but not null
     */
    Set<AdvisorDefinition> findMatchingServiceAdvisors(ServiceDefinition serviceDef);

    /**
     * Finds any contributions that are targeted at the indicated service.
     */
    Set<ContributionDef> getContributorDefsForService(ServiceDefinition serviceDef);

    /**
     * Locates services with the {@link com.jujin.freeway.ioc.annotations.EagerLoad}
     * annotation and generates proxies for them, then adds them to the proxies list
     * for instantiation.
     *
     * @param proxies
     *            collection of proxies to which any eager load services in the
     *            module should be added
     */
    void collectEagerLoadServices(Collection<EagerLoadServiceProxy> proxies);

    /**
     * Returns the service definition for the given service id.
     *
     * @param serviceId
     *            unique id for the service (caseless)
     * @return the service definition or null
     */
    ServiceDefinition getServiceDef(String serviceId);

    /**
     * Returns the name used to obtain a logger for the module. Services within the
     * module suffix this with a period and the service id.
     *
     * @return module logger name
     */
    String getLoggerName();
}
