package com.jujin.freeway.ioc;

import static com.jujin.freeway.ioc.config.OrderConstraintBuilder.after;
import static com.jujin.freeway.ioc.config.OrderConstraintBuilder.before;

import com.jujin.freeway.ioc.advisor.ThunkCreator;
import com.jujin.freeway.ioc.annotations.*;
import com.jujin.freeway.ioc.coercion.TypeCoercer;
import com.jujin.freeway.ioc.config.MappedConfiguration;
import com.jujin.freeway.ioc.config.OrderedConfiguration;
import com.jujin.freeway.ioc.config.ServiceConfigurationListenerHub;
import com.jujin.freeway.ioc.exception.ExceptionAnalyzer;
import com.jujin.freeway.ioc.exception.ExceptionTracker;
import com.jujin.freeway.ioc.internal.*;
import com.jujin.freeway.ioc.internal.cron.PeriodicExecutorImpl;
import com.jujin.freeway.ioc.internal.util.IocConstants;
import com.jujin.freeway.ioc.internal.util.Scopes;
import com.jujin.freeway.ioc.lifecycle.PerThreadManager;
import com.jujin.freeway.ioc.lifecycle.ServiceLifecycle;
import com.jujin.freeway.ioc.lifecycle.ServiceLifecycleSource;
import com.jujin.freeway.ioc.lifecycle.ThreadLocale;
import com.jujin.freeway.ioc.schedule.PeriodicExecutor;
import com.jujin.freeway.ioc.schedule.TimeInterval;
import com.jujin.freeway.ioc.symbol.SymbolSource;
import com.jujin.freeway.ioc.threading.ParallelExecutor;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.concurrent.Executors;

/**
 * Defines the base set of services for the Freeway IOC container.
 * <p>
 * This module configures the core injection infrastructure — the injector,
 * symbol source, type coercer, thread/scope management, and the parallel
 * executor. Additional capabilities (AOP advisors, type coercion rules,
 * symbol sources, classpath scanning) are contributed by sub-modules declared
 * via {@link ImportModule}.
 */
@Marker(Builtin.class)
@ImportModule(
    {
        AdvisorModule.class,
        CoercionModule.class,
        SymbolModule.class,
        ScanModule.class,
    }
)
public final class FreewayIOCModule {

    public static void bind(ServiceBinder binder) {
        binder
            .bind(InjectionResolver.class, InjectionResolverImpl.class)
            .preventReloading();
        binder
            .bind(TypeCoercer.class, TypeCoercerImpl.class)
            .preventReloading();
        binder.bind(SymbolSource.class, SymbolSourceImpl.class);
        binder.bind(ThreadLocale.class, ThreadLocaleImpl.class);
        binder.bind(ExceptionTracker.class, ExceptionTrackerImpl.class);
        binder.bind(ExceptionAnalyzer.class, ExceptionAnalyzerImpl.class);
        binder.bind(ServiceConfigurationListenerHub.class);
        binder.bind(ServiceOverride.class, ServiceOverrideImpl.class);
        binder.bind(Runnable.class, RegistryStartup.class).withSimpleId();
        binder.bind(PeriodicExecutor.class, PeriodicExecutorImpl.class);
    }

    @PreventServiceDecoration
    public static ServiceLifecycleSource build(
        Map<String, ServiceLifecycle> configuration
    ) {
        var lifecycles = new TreeMap<String, ServiceLifecycle>(
            String.CASE_INSENSITIVE_ORDER
        );
        for (Entry<String, ServiceLifecycle> entry : configuration.entrySet()) {
            lifecycles.put(entry.getKey(), entry.getValue());
        }
        return new ServiceLifecycleSource() {
            @Override
            public ServiceLifecycle get(String scope) {
                return lifecycles.get(scope);
            }
        };
    }

    @Contribute(ServiceLifecycleSource.class)
    public static void providePerthreadScope(
        MappedConfiguration<String, ServiceLifecycle> configuration
    ) {
        configuration.addInstance(
            Scopes.PERTHREAD,
            PerThreadServiceLifecycle.class
        );
    }

    @Contribute(InjectionResolver.class)
    public static void setupObjectProviders(
        OrderedConfiguration<InjectionProvider> configuration,
        final @Local ServiceOverride serviceOverride
    ) {
        configuration.add("AnnotationBasedContributions", null);
        configuration.addInstance(
            "Config",
            BuiltinConfigProvider.class,
            before("AnnotationBasedContributions").build()
        );
        configuration.add(
            "Autobuild",
            new BuiltinAutobuildProvider(),
            before("AnnotationBasedContributions").build()
        );

        InjectionProvider wrapper = new InjectionProvider() {
            @Override
            public <T> T provide(
                Class<T> objectType,
                AnnotationProvider annotationProvider,
                ServiceLocator locator
            ) {
                return serviceOverride
                    .getServiceOverrideProvider()
                    .provide(objectType, annotationProvider, locator);
            }
        };
        configuration.add(
            "ServiceOverride",
            wrapper,
            after("AnnotationBasedContributions").build()
        );
    }

    public static ParallelExecutor buildDeferredExecution(
        @Symbol(IocConstants.THREAD_POOL_CORE_SIZE) int coreSize,
        @Symbol(IocConstants.THREAD_POOL_MAX_SIZE) int maxSize,
        @Symbol(IocConstants.THREAD_POOL_KEEP_ALIVE) @IntermediateType(
            TimeInterval.class
        ) int keepAliveMillis,
        @Symbol(IocConstants.THREAD_POOL_ENABLED) boolean threadPoolEnabled,
        @Symbol(IocConstants.THREAD_POOL_QUEUE_SIZE) int queueSize,
        PerThreadManager perthreadManager,
        RegistryShutdownHub shutdownHub,
        ThunkCreator thunkCreator
    ) {
        if (!threadPoolEnabled) return new NonParallelExecutor();
        var executorService = Executors.newVirtualThreadPerTaskExecutor();
        shutdownHub.addRegistryShutdownListener(
            (Runnable) executorService::shutdown
        );
        return new ParallelExecutorImpl(
            executorService,
            thunkCreator,
            perthreadManager
        );
    }

    public static void contributeRegistryStartup(
        OrderedConfiguration<Runnable> configuration,
        PeriodicExecutor periodicExecutor
    ) {
        configuration.add(PeriodicExecutor.class.getSimpleName(), () ->
            periodicExecutor.init()
        );
    }
}
