package com.jujin.freeway.boot.config;

import com.jujin.freeway.ioc.*;
import com.jujin.freeway.ioc.advisor.AdvisorDefinition;
import com.jujin.freeway.ioc.lifecycle.ObjectCreator;
import com.jujin.freeway.ioc.lifecycle.StartupDef;
import com.jujin.freeway.ioc.internal.MapSymbolProvider;
import com.jujin.freeway.ioc.symbol.SymbolProvider;
import java.util.*;

/**
 * ModuleDefinition that bridges boot-merged configuration into the SymbolSource.
 * <p>
 * Creates a {@link MapSymbolProvider} from the merged config map and
 * contributes it into the ordered {@link SymbolProvider} chain after
 * {@code EnvironmentVariables} and before {@code ApplicationDefaults}.
 */
public class BootConfigModuleDefinition implements ModuleDefinition {

    private final Map<String, String> config;

    public BootConfigModuleDefinition(Map<String, String> config) {
        this.config = Objects.requireNonNull(config, "config");
    }

    @Override
    public Set<String> getServiceIds() {
        return Set.of("BootConfig");
    }

    @Override
    public ServiceDefinition getServiceDef(String serviceId) {
        if (!"BootConfig".equalsIgnoreCase(serviceId))
            return null;
        return new ServiceDefinition() {
            @Override
            public String getServiceId() {
                return "BootConfig";
            }

            @Override
            public Class<?> getServiceInterface() {
                return SymbolProvider.class;
            }

            @Override
            public boolean isEagerLoad() {
                return false;
            }

            @Override
            public String getServiceScope() {
                return "singleton";
            }

            @Override
            public Set<Class<?>> getMarkers() {
                return Set.of();
            }

            @Override
            public ObjectCreator<?> createServiceCreator(
                ServiceBuilderResources resources) {
                return () -> new MapSymbolProvider(config);
            }

            @Override
            public AnnotationProvider getClassAnnotationProvider() {
                return com.jujin.freeway.ioc.internal.util.InternalUtils.toAnnotationProvider(
                    getServiceInterface());
            }

            @Override
            @SuppressWarnings("rawtypes")
            public AnnotationProvider getMethodAnnotationProvider(
                String methodName,
                Class... argumentTypes) {
                return com.jujin.freeway.ioc.internal.util.InternalUtils.toAnnotationProvider(
                    com.jujin.freeway.ioc.internal.util.InternalUtils.findMethod(
                        getServiceInterface(),
                        methodName,
                        argumentTypes));
            }
        };
    }

    @Override
    public Set<ContributionDef> getContributionDefs() {
        ContributionDef contribution = new ContributionDef() {
            @Override
            public String getServiceId() {
                return "SymbolSource";
            }

            @Override
            public Set<Class<?>> getMarkers() {
                return Set.of();
            }

            @Override
            public Class<?> getServiceInterface() {
                return null;
            }

            @Override
            @SuppressWarnings("rawtypes")
            public void contribute(
                ModuleBuilderSource moduleSource,
                ServiceResources resources,
                Configuration configuration) {
                throw new UnsupportedOperationException(
                    "Unordered configuration not supported");
            }

            @Override
            @SuppressWarnings({ "rawtypes", "unchecked" })
            public void contribute(
                ModuleBuilderSource moduleSource,
                ServiceResources resources,
                OrderedConfiguration ordered) {
                SymbolProvider bootProvider = new MapSymbolProvider(config);
                ordered.add(
                    "BootConfig",
                    bootProvider,
                    "after:EnvironmentVariables",
                    "before:ApplicationDefaults");
            }

            @Override
            @SuppressWarnings("rawtypes")
            public void contribute(
                ModuleBuilderSource moduleSource,
                ServiceResources resources,
                MappedConfiguration mapped) {
                throw new UnsupportedOperationException(
                    "Mapped configuration not supported");
            }
        };
        return Set.of(contribution);
    }

    @Override
    public Set<AdvisorDefinition> getAdvisorDefs() {
        return Set.of();
    }

    @Override
    public Set<StartupDef> getStartups() {
        return Set.of();
    }

    @Override
    public Class<?> getBuilderClass() {
        return BootConfigModuleDefinition.class;
    }

    @Override
    public String getLoggerName() {
        return "BootConfig";
    }
}
