package com.jujin.freeway.ioc.advisor.internal;

import com.jujin.freeway.ioc.ServiceDefinition;
import com.jujin.freeway.ioc.internal.GlobIdMatcher;
import com.jujin.freeway.ioc.internal.IdMatcher;
import com.jujin.freeway.ioc.internal.JdkProxyFactory;
import com.jujin.freeway.ioc.internal.OrIdMatcher;
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
    Class<?> serviceInterface
) {
    public ServiceInstrumenterConfig {
        constraints = constraints != null ? constraints.clone() : new String[0];
    }

    public static ServiceInstrumenterConfig of(
        Method method,
        String[] patterns,
        String[] constraints,
        Class<?> serviceInterface,
        Set<Class<?>> markers,
        JdkProxyFactory proxyFactory
    ) {
        List<IdMatcher> matchers = new ArrayList<>();
        for (String pattern : patterns) {
            matchers.add(new GlobIdMatcher(pattern));
        }
        return new ServiceInstrumenterConfig(
            method,
            new OrIdMatcher(matchers),
            constraints,
            proxyFactory,
            markers,
            serviceInterface
        );
    }

    public boolean matches(ServiceDefinition serviceDef) {
        return idMatcher.matches(serviceDef.getServiceId());
    }
}
