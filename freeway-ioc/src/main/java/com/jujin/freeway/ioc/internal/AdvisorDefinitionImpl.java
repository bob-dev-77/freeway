package com.jujin.freeway.ioc.internal;

import com.jujin.freeway.ioc.ServiceDefinition;
import com.jujin.freeway.ioc.config.*;
import com.jujin.freeway.ioc.property.*;
import com.jujin.freeway.ioc.threading.*;
import com.jujin.freeway.ioc.classpath.*;
import com.jujin.freeway.ioc.exception.*;
import com.jujin.freeway.ioc.advisor.AdvisorDefinition;
import com.jujin.freeway.ioc.ModuleBuilderSource;
import com.jujin.freeway.ioc.config.*;
import com.jujin.freeway.ioc.property.*;
import com.jujin.freeway.ioc.threading.*;
import com.jujin.freeway.ioc.classpath.*;
import com.jujin.freeway.ioc.exception.*;
import com.jujin.freeway.ioc.advisor.ServiceAdvisor;
import com.jujin.freeway.ioc.ServiceResources;
import com.jujin.freeway.ioc.config.*;
import com.jujin.freeway.ioc.property.*;
import com.jujin.freeway.ioc.threading.*;
import com.jujin.freeway.ioc.classpath.*;
import com.jujin.freeway.ioc.exception.*;
import com.jujin.freeway.ioc.internal.util.InternalUtils;
import java.lang.reflect.Method;
import java.util.Set;

public class AdvisorDefinitionImpl implements AdvisorDefinition {

    private final ServiceInstrumenterConfig config;
    private final String advisorId;

    public AdvisorDefinitionImpl(
        Method method,
        String[] patterns,
        String[] constraints,
        JdkProxyFactory proxyFactory,
        String advisorId,
        Class<?> serviceInterface,
        Set<Class<?>> markers) {
        assert InternalUtils.isNonBlank(advisorId);
        validateConstraints(constraints, advisorId);

        this.config = ServiceInstrumenterConfig.of(
            method,
            patterns,
            constraints,
            serviceInterface,
            markers,
            proxyFactory);
        this.advisorId = advisorId;
    }

    private static void validateConstraints(String[] constraints, String advisorId) {
        if (constraints == null)
            return;
        for (String c : constraints) {
            int colon = c.indexOf(':');
            if (colon <= 0)
                throw new IllegalArgumentException(
                    "Invalid constraint '" + c + "' for advisor '" + advisorId +
                    "': expected format 'before:<id>' or 'after:<id>'");
            String type = c.substring(0, colon);
            if (!"before".equals(type) && !"after".equals(type))
                throw new IllegalArgumentException(
                    "Invalid constraint '" + c + "' for advisor '" + advisorId +
                    "': type must be 'before' or 'after'");
        }
    }

    @Override
    public ServiceAdvisor createAdvisor(
        ModuleBuilderSource moduleSource,
        ServiceResources resources) {
        return new ServiceAdvisorImpl(
            moduleSource,
            config.method(),
            resources,
            config.proxyFactory());
    }

    @Override
    public String getAdvisorId() {
        return advisorId;
    }

    @Override
    public String[] getConstraints() {
        return config.constraints();
    }

    @Override
    public boolean matches(ServiceDefinition serviceDef) {
        return config.matches(serviceDef);
    }

    @Override
    public Set<Class<?>> getMarkers() {
        return config.markers();
    }

    @Override
    public Class<?> getServiceInterface() {
        return config.serviceInterface();
    }

    @Override
    public String toString() {
        return InternalUtils.asString(config.method());
    }
}
