package com.jujin.freeway.ioc.internal;

import com.jujin.freeway.ioc.ServiceDefinition;
import com.jujin.freeway.ioc.config.*;
import com.jujin.freeway.ioc.property.*;
import com.jujin.freeway.ioc.threading.*;
import com.jujin.freeway.ioc.classpath.*;
import com.jujin.freeway.ioc.exception.*;
import com.jujin.freeway.ioc.internal.util.InternalUtils;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Shared configuration for service instrumenters (decorators and advisors).
 * Extracted from AbstractServiceInstrumenter as a composition-based
 * alternative.
 */
public record ServiceInstrumenterConfig(
    Method method,
    IdMatcher idMatcher,
    String[] constraints,
    JdkProxyFactory proxyFactory,
    Set<Class<?>> markers,
    Class<?> serviceInterface) {
    public ServiceInstrumenterConfig {
        constraints = constraints != null ? constraints.clone() : new String[0];
    }

    public static ServiceInstrumenterConfig of(
        Method method,
        String[] patterns,
        String[] constraints,
        Class<?> serviceInterface,
        Set<Class<?>> markers,
        JdkProxyFactory proxyFactory) {
        List<IdMatcher> matchers = new ArrayList<>();
        for (String pattern : patterns) {
            matchers.add(new InternalUtils.IdMatcherImpl(pattern));
        }
        return new ServiceInstrumenterConfig(
            method,
            new InternalUtils.OrIdMatcher(matchers),
            constraints,
            proxyFactory,
            markers,
            serviceInterface);
    }

    public boolean matches(ServiceDefinition serviceDef) {
        return idMatcher.matches(serviceDef.getServiceId());
    }
}
