package com.jujin.freeway.ioc.internal;

import com.jujin.freeway.ioc.ModuleInstanceSource;
import com.jujin.freeway.ioc.ServiceContext;
import com.jujin.freeway.ioc.ServiceLocator;
import com.jujin.freeway.ioc.config.Configuration;
import com.jujin.freeway.ioc.config.ContributionDef;
import com.jujin.freeway.ioc.config.MappedConfiguration;
import com.jujin.freeway.ioc.config.OrderedConfiguration;
import com.jujin.freeway.ioc.internal.util.*;
import com.jujin.freeway.ioc.lifecycle.ObjectCreator;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;

public class ContributionDefImpl implements ContributionDef {

    private final String serviceId;

    private final Method contributorMethod;

    private final boolean optional;

    private final Set<Class<?>> markers;

    private final Class<?> serviceInterface;

    private static final Class<?>[] CONFIGURATION_TYPES = new Class<?>[] {
        Configuration.class,
        MappedConfiguration.class,
        OrderedConfiguration.class,
    };

    public ContributionDefImpl(
        String serviceId,
        Method contributorMethod,
        boolean optional,
        JdkProxyFactory proxyFactory,
        Class<?> serviceInterface,
        Set<Class<?>> markers
    ) {
        this.serviceId = serviceId;
        this.contributorMethod = contributorMethod;
        this.optional = optional;
        this.serviceInterface = serviceInterface;
        this.markers = markers;
    }

    @Override
    public String toString() {
        return DisplayUtils.asString(contributorMethod);
    }

    @Override
    public boolean isOptional() {
        return optional;
    }

    @Override
    public String getServiceId() {
        return serviceId;
    }

    @Override
    @SuppressWarnings("rawtypes")
    public void contribute(
        ModuleInstanceSource moduleSource,
        ServiceContext resources,
        Configuration configuration
    ) {
        invokeMethod(
            moduleSource,
            resources,
            Configuration.class,
            configuration
        );
    }

    @Override
    @SuppressWarnings("rawtypes")
    public void contribute(
        ModuleInstanceSource moduleSource,
        ServiceContext resources,
        OrderedConfiguration configuration
    ) {
        invokeMethod(
            moduleSource,
            resources,
            OrderedConfiguration.class,
            configuration
        );
    }

    @Override
    @SuppressWarnings("rawtypes")
    public void contribute(
        ModuleInstanceSource moduleSource,
        ServiceContext resources,
        MappedConfiguration configuration
    ) {
        invokeMethod(
            moduleSource,
            resources,
            MappedConfiguration.class,
            configuration
        );
    }

    private <T> void invokeMethod(
        ModuleInstanceSource source,
        ServiceContext resources,
        Class<T> parameterType,
        T parameterValue
    ) {
        Map<Class<?>, Object> resourceMap = new HashMap<>();

        resourceMap.put(parameterType, parameterValue);
        resourceMap.put(ServiceLocator.class, resources);
        resourceMap.put(Logger.class, resources.getLogger());

        InjectionContext injectionContext = new MappedInjectionContext(
            resourceMap
        );

        // For each of the other configuration types that is not expected, add a guard.

        for (Class<?> t : CONFIGURATION_TYPES) {
            if (parameterType != t) {
                injectionContext = new DelegatingInjectionContext(
                    new WrongConfigurationTypeGuard(
                        resources.getServiceId(),
                        t,
                        parameterType
                    ),
                    injectionContext
                );
            }
        }

        Throwable fail = null;

        Object moduleInstance = ReflectionSupport.isStatic(contributorMethod)
            ? null
            : source.getInstance();

        try {
            @SuppressWarnings("rawtypes")
            ObjectCreator[] parameters =
                InjectionPlanner.resolveMethodParameters(
                    contributorMethod,
                    resources,
                    injectionContext,
                    resources.getTracker()
                );

            contributorMethod.invoke(
                moduleInstance,
                InjectionPlanner.realizeAll(parameters)
            );
        } catch (InvocationTargetException ex) {
            fail = ex.getTargetException();
        } catch (Exception ex) {
            fail = ex;
        }

        if (fail != null) throw new RuntimeException(
            String.format(
                "Error invoking service contribution method %s: %s",
                DisplayUtils.asString(contributorMethod),
                fail.getMessage()
            ),
            fail
        );
    }

    @Override
    public Set<Class<?>> getMarkers() {
        return markers;
    }

    @Override
    public Class<?> getServiceInterface() {
        return serviceInterface;
    }
}
