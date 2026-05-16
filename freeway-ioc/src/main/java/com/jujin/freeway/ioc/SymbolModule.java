package com.jujin.freeway.ioc;

import com.jujin.freeway.ioc.annotations.ApplicationDefaults;
import com.jujin.freeway.ioc.annotations.Builtin;
import com.jujin.freeway.ioc.annotations.Contribute;
import com.jujin.freeway.ioc.annotations.FactoryDefaults;
import com.jujin.freeway.ioc.annotations.Marker;
import com.jujin.freeway.ioc.config.MappedConfiguration;
import com.jujin.freeway.ioc.config.OrderedConfiguration;
import com.jujin.freeway.ioc.internal.MapSymbolProvider;
import com.jujin.freeway.ioc.internal.SystemEnvSymbolProvider;
import com.jujin.freeway.ioc.internal.SystemPropertiesSymbolProvider;
import com.jujin.freeway.ioc.internal.util.IocConstants;
import com.jujin.freeway.ioc.symbol.SymbolProvider;
import com.jujin.freeway.ioc.symbol.SymbolSource;

@Marker(Builtin.class)
public class SymbolModule {

    public static void bind(ServiceBinder binder) {
        binder.bind(SymbolProvider.class, MapSymbolProvider.class)
            .withId("ApplicationDefaults")
            .withMarker(ApplicationDefaults.class);
        binder.bind(SymbolProvider.class, MapSymbolProvider.class)
            .withId("FactoryDefaults")
            .withMarker(FactoryDefaults.class);
    }

    @Contribute(SymbolSource.class)
    public static void setupStandardSymbolProviders(
        OrderedConfiguration<SymbolProvider> configuration,
        @ApplicationDefaults SymbolProvider applicationDefaults,
        @FactoryDefaults SymbolProvider factoryDefaults
    ) {
        configuration.add("SystemProperties", new SystemPropertiesSymbolProvider(), "before:*");
        configuration.add("EnvironmentVariables", new SystemEnvSymbolProvider());
        configuration.add("ApplicationDefaults", applicationDefaults);
        configuration.add("FactoryDefaults", factoryDefaults);
    }

    @Contribute(SymbolProvider.class)
    @FactoryDefaults
    public static void setupDefaultSymbols(
        MappedConfiguration<String, Object> configuration
    ) {
        configuration.add(IocConstants.THREAD_POOL_CORE_SIZE, 3);
        configuration.add(IocConstants.THREAD_POOL_MAX_SIZE, 20);
        configuration.add(IocConstants.THREAD_POOL_KEEP_ALIVE, "1 m");
        configuration.add(IocConstants.THREAD_POOL_ENABLED, true);
        configuration.add(IocConstants.THREAD_POOL_QUEUE_SIZE, 100);
        configuration.add(IocConstants.PROXY_MECHANISM, "jdk");
    }
}
