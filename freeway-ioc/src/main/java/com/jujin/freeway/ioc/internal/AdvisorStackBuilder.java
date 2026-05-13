package com.jujin.freeway.ioc.internal;

import com.jujin.freeway.ioc.ServiceDefinition;
import com.jujin.freeway.ioc.advisor.AspectInterceptor;
import com.jujin.freeway.ioc.advisor.AspectInterceptorBuilder;
import com.jujin.freeway.ioc.advisor.ServiceAdvisor;
import com.jujin.freeway.ioc.lifecycle.ObjectCreator;

import java.util.List;

/**
 * Builds the interceptor stack for a service by collecting
 * {@link com.jujin.freeway.ioc.advisor.ServiceAdvisor}s from all modules
 * and wrapping the service creator with an
 * {@link com.jujin.freeway.ioc.advisor.AspectInterceptorBuilder}.
 *
 */
public class AdvisorStackBuilder implements ObjectCreator<Object> {

    private final ServiceDefinition serviceDef;

    private final ObjectCreator<?> delegate;

    private final AspectInterceptor interceptor;

    private final InternalRegistry registry;

    /**
     * @param serviceDef
     *            the service that is ultimately being constructed
     * @param delegate
     *            responsible for creating the object to be decorated
     * @param interceptor
     *            used to create the
     *            {@link com.jujin.freeway.ioc.advisor.AspectInterceptorBuilder}
     *            passed to each
     *            {@link com.jujin.freeway.ioc.advisor.ServiceAdvisor}
     * @param registry
     */
    public AdvisorStackBuilder(
        ServiceDefinition serviceDef,
        ObjectCreator<?> delegate,
        AspectInterceptor interceptor,
        InternalRegistry registry) {
        this.serviceDef = serviceDef;
        this.delegate = delegate;
        this.registry = registry;
        this.interceptor = interceptor;
    }

    @Override
    public Object create() {
        Object service = delegate.create();

        List<ServiceAdvisor> advisors = registry.findAdvisorsForService(
            serviceDef);

        if (advisors.isEmpty())
            return service;

        @SuppressWarnings({ "unchecked", "rawtypes" })
        final AspectInterceptorBuilder builder = interceptor.createBuilder(
            (Class) serviceDef.getServiceInterface(),
            service,
            serviceDef,
            String.format(
                "<AspectProxy for %s(%s)>",
                serviceDef.getServiceId(),
                serviceDef.getServiceInterface().getName()));

        for (final ServiceAdvisor advisor : advisors) {
            registry.run("Invoking " + advisor, () -> advisor.advise(builder));
        }

        return builder.build();
    }
}
