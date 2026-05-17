package com.jujin.freeway.boot.config;

import com.jujin.freeway.ioc.*;
import com.jujin.freeway.ioc.advisor.AdvisorDefinition;
import com.jujin.freeway.ioc.config.Configuration;
import com.jujin.freeway.ioc.config.ContributionDef;
import com.jujin.freeway.ioc.config.MappedConfiguration;
import com.jujin.freeway.ioc.config.OrderedConfiguration;
import com.jujin.freeway.ioc.lifecycle.StartupDef;
import com.jujin.freeway.ioc.symbol.SymbolProvider;
import com.jujin.freeway.ioc.symbol.internal.MapSymbolProvider;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * ModuleDefinition that bridges boot-merged configuration into the SymbolSource.
 * <p>
 * Contributes a {@link MapSymbolProvider} into the ordered {@link SymbolProvider}
 * chain after {@code EnvironmentVariables} and before {@code ApplicationDefaults}.
 */
public class BootModuleDefinition implements ModuleDefinition {

    private final Map<String, String> config;

    public BootModuleDefinition(Map<String, String> config) {
        this.config = Objects.requireNonNull(config, "config");
    }

    @Override
    public Set<String> getServiceIds() {
        return Set.of(); // No services exposed - only contributes to SymbolSource
    }

    @Override
    public ServiceDefinition getServiceDef(String serviceId) {
        return null; // No service definitions - see getContributionDefs()
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
                ModuleInstanceSource moduleSource,
                ServiceContext resources,
                Configuration configuration
            ) {
                throw new UnsupportedOperationException(
                    "Unordered configuration not supported"
                );
            }

            @Override
            @SuppressWarnings({ "rawtypes", "unchecked" })
            public void contribute(
                ModuleInstanceSource moduleSource,
                ServiceContext resources,
                OrderedConfiguration ordered
            ) {
                SymbolProvider bootProvider = new MapSymbolProvider(config);
                ordered.add(
                    "BootConfig",
                    bootProvider,
                    "after:EnvironmentVariables",
                    "before:ApplicationDefaults"
                );
            }

            @Override
            @SuppressWarnings("rawtypes")
            public void contribute(
                ModuleInstanceSource moduleSource,
                ServiceContext resources,
                MappedConfiguration mapped
            ) {
                throw new UnsupportedOperationException(
                    "Mapped configuration not supported"
                );
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
        return BootModuleDefinition.class;
    }

    @Override
    public String getLoggerName() {
        return "BootConfig";
    }
}
