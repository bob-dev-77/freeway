package com.jujin.freeway.ioc.internal;

import com.jujin.freeway.ioc.*;
import com.jujin.freeway.ioc.config.*;
import com.jujin.freeway.ioc.property.*;
import com.jujin.freeway.ioc.threading.*;
import com.jujin.freeway.ioc.classpath.*;
import com.jujin.freeway.ioc.exception.*;
import com.jujin.freeway.ioc.lifecycle.*;
import com.jujin.freeway.ioc.ModuleBuilderSource;
import com.jujin.freeway.ioc.ServiceResources;
import com.jujin.freeway.ioc.config.*;
import com.jujin.freeway.ioc.property.*;
import com.jujin.freeway.ioc.threading.*;
import com.jujin.freeway.ioc.classpath.*;
import com.jujin.freeway.ioc.exception.*;
import com.jujin.freeway.ioc.internal.util.DelegatingInjectionResources;
import com.jujin.freeway.ioc.config.*;
import com.jujin.freeway.ioc.property.*;
import com.jujin.freeway.ioc.threading.*;
import com.jujin.freeway.ioc.classpath.*;
import com.jujin.freeway.ioc.exception.*;
import com.jujin.freeway.ioc.internal.util.InjectionResources;
import com.jujin.freeway.ioc.config.*;
import com.jujin.freeway.ioc.property.*;
import com.jujin.freeway.ioc.threading.*;
import com.jujin.freeway.ioc.classpath.*;
import com.jujin.freeway.ioc.exception.*;
import com.jujin.freeway.ioc.internal.util.InternalUtils;
import com.jujin.freeway.ioc.config.*;
import com.jujin.freeway.ioc.property.*;
import com.jujin.freeway.ioc.threading.*;
import com.jujin.freeway.ioc.classpath.*;
import com.jujin.freeway.ioc.exception.*;
import com.jujin.freeway.ioc.internal.util.MapInjectionResources;
import com.jujin.freeway.ioc.config.*;
import com.jujin.freeway.ioc.property.*;
import com.jujin.freeway.ioc.threading.*;
import com.jujin.freeway.ioc.classpath.*;
import com.jujin.freeway.ioc.exception.*;
import com.jujin.freeway.ioc.internal.util.WrongConfigurationTypeGuard;
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

    private static final Class<?>[] CONFIGURATION_TYPES = new Class<?>[]{
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
        Set<Class<?>> markers) {
        this.serviceId = serviceId;
        this.contributorMethod = contributorMethod;
        this.optional = optional;
        this.serviceInterface = serviceInterface;
        this.markers = markers;
    }

    @Override
    public String toString() {
        return InternalUtils.asString(contributorMethod);
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
        ModuleBuilderSource moduleSource,
        ServiceResources resources,
        Configuration configuration) {
        invokeMethod(
            moduleSource,
            resources,
            Configuration.class,
            configuration);
    }

    @Override
    @SuppressWarnings("rawtypes")
    public void contribute(
        ModuleBuilderSource moduleSource,
        ServiceResources resources,
        OrderedConfiguration configuration) {
        invokeMethod(
            moduleSource,
            resources,
            OrderedConfiguration.class,
            configuration);
    }

    @Override
    @SuppressWarnings("rawtypes")
    public void contribute(
        ModuleBuilderSource moduleSource,
        ServiceResources resources,
        MappedConfiguration configuration) {
        invokeMethod(
            moduleSource,
            resources,
            MappedConfiguration.class,
            configuration);
    }

    private <T> void invokeMethod(
        ModuleBuilderSource source,
        ServiceResources resources,
        Class<T> parameterType,
        T parameterValue) {
        Map<Class<?>, Object> resourceMap = new HashMap<>();

        resourceMap.put(parameterType, parameterValue);
        resourceMap.put(ServiceLocator.class, resources);
        resourceMap.put(Logger.class, resources.getLogger());

        InjectionResources injectionResources = new MapInjectionResources(
            resourceMap);

        // For each of the other configuration types that is not expected, add a guard.

        for (Class<?> t : CONFIGURATION_TYPES) {
            if (parameterType != t) {
                injectionResources = new DelegatingInjectionResources(
                    new WrongConfigurationTypeGuard(
                        resources.getServiceId(),
                        t,
                        parameterType),
                    injectionResources);
            }
        }

        Throwable fail = null;

        Object moduleInstance = InternalUtils.isStatic(contributorMethod)
            ? null
            : source.getModuleBuilder();

        try {
            @SuppressWarnings("rawtypes")
            ObjectCreator[] parameters = InternalUtils.calculateParametersForMethod(
                contributorMethod,
                resources,
                injectionResources,
                resources.getTracker());

            contributorMethod.invoke(
                moduleInstance,
                InternalUtils.realizeObjects(parameters));
        } catch (InvocationTargetException ex) {
            fail = ex.getTargetException();
        } catch (Exception ex) {
            fail = ex;
        }

        if (fail != null)
            throw new RuntimeException(
                IOCMessages.contributionMethodError(contributorMethod, fail),
                fail);
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
