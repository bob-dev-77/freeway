package com.jujin.freeway.ioc;

import static com.jujin.freeway.ioc.config.OrderConstraintBuilder.after;
import static com.jujin.freeway.ioc.config.OrderConstraintBuilder.before;

import com.jujin.freeway.ioc.advisor.*;
import com.jujin.freeway.ioc.annotations.*;
import com.jujin.freeway.ioc.classpath.ClassNameLocator;
import com.jujin.freeway.ioc.classpath.ClassPathScanner;
import com.jujin.freeway.ioc.classpath.ClassPathURLConverter;
import com.jujin.freeway.ioc.coercion.CoercionTuple;
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
import com.jujin.freeway.ioc.property.PropertyAccess;
import com.jujin.freeway.ioc.property.PropertyShadowBuilder;
import com.jujin.freeway.ioc.schedule.PeriodicExecutor;
import com.jujin.freeway.ioc.schedule.TimeInterval;
import com.jujin.freeway.ioc.symbol.SymbolProvider;
import com.jujin.freeway.ioc.symbol.SymbolSource;
import com.jujin.freeway.ioc.threading.ParallelExecutor;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.concurrent.Executors;

/**
 * Defines the base set of services for the Freeway IOC container.
 */
@Marker(Builtin.class)
public final class FreewayIOCModule {

    public static void bind(ServiceBinder binder) {
        binder.bind(LoggingInterceptor.class, LoggingInterceptorImpl.class);
        binder.bind(ChainBuilder.class, ChainBuilderImpl.class);
        binder.bind(PropertyAccess.class, PropertyAccessImpl.class);
        binder.bind(StrategyBuilder.class, StrategyBuilderImpl.class);
        binder.bind(
            PropertyShadowBuilder.class,
            PropertyShadowBuilderImpl.class
        );
        binder
            .bind(PipelineBuilder.class, PipelineBuilderImpl.class)
            .preventReloading();
        binder.bind(
            DefaultServiceProxyBuilder.class,
            DefaultServiceProxyBuilderImpl.class
        );
        binder.bind(ExceptionTracker.class, ExceptionTrackerImpl.class);
        binder.bind(ExceptionAnalyzer.class, ExceptionAnalyzerImpl.class);
        binder
            .bind(TypeCoercer.class, TypeCoercerImpl.class)
            .preventReloading();
        binder.bind(ThreadLocale.class, ThreadLocaleImpl.class);
        binder.bind(SymbolSource.class, SymbolSourceImpl.class);
        binder
            .bind(SymbolProvider.class, MapSymbolProvider.class)
            .withId("ApplicationDefaults")
            .withMarker(ApplicationDefaults.class);
        binder
            .bind(SymbolProvider.class, MapSymbolProvider.class)
            .withId("FactoryDefaults")
            .withMarker(FactoryDefaults.class);
        binder.bind(Runnable.class, RegistryStartup.class).withSimpleId();
        binder
            .bind(InjectionResolver.class, InjectionResolverImpl.class)
            .preventReloading();
        binder.bind(ClassNameLocator.class, ClassNameLocatorImpl.class);
        binder.bind(ClassPathScanner.class, ClassPathScannerImpl.class);
        binder.bind(AspectInterceptor.class, AspectDecoratorImpl.class);
        binder.bind(
            ClassPathURLConverter.class,
            ClassPathURLConverterImpl.class
        );
        binder.bind(ServiceOverride.class, ServiceOverrideImpl.class);
        binder.bind(LoggingAdvisor.class, LoggingAdvisorImpl.class);
        binder.bind(LazyAdvisor.class, LazyAdvisorImpl.class);
        binder.bind(ThunkCreator.class, ThunkCreatorImpl.class);
        binder
            .bind(UpdateListenerHub.class, UpdateListenerHubImpl.class)
            .preventReloading();
        binder.bind(PeriodicExecutor.class, PeriodicExecutorImpl.class);
        binder.bind(OperationAdvisor.class, OperationAdvisorImpl.class);
        binder.bind(ServiceConfigurationListenerHub.class);
    }

    /**
     * Provides access to additional service lifecycles. One lifecycle is built in
     * ("singleton") but additional ones are accessed via this service (and its
     * mapped configuration). Only proxiable services (those with explicit service
     * interfaces) can be managed in terms of a lifecycle.
     */
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

    /**
     * Contributes the "perthread" scope.
     */
    @Contribute(ServiceLifecycleSource.class)
    public static void providePerthreadScope(
        MappedConfiguration<String, ServiceLifecycle> configuration
    ) {
        configuration.addInstance(
            Scopes.PERTHREAD,
            PerThreadServiceLifecycle.class
        );
    }

    /**
     * <dl>
     * <dt>AnnotationBasedContributions</dt>
     * <dd>Empty placeholder used to separate annotation-based InjectionProvider
     * contributions (which come before) from non-annotation based (such as
     * ServiceOverride) which come after.</dd>
     * <dt>Config</dt>
     * <dd>Supports the {@link com.jujin.freeway.ioc.annotations.Value} and
     * {@link com.jujin.freeway.ioc.annotations.Symbol} annotations</dd>
     * <dt>Autobuild</dt>
     * <dd>Supports the {@link com.jujin.freeway.ioc.annotations.Autobuild}
     * annotation</dd>
     * <dt>ServiceOverride</dt>
     * <dd>Allows simple service overrides via the
     * {@link com.jujin.freeway.ioc.ServiceOverride} service (and its configuration)
     * </dl>
     */
    @Contribute(InjectionResolver.class)
    public static void setupObjectProviders(
        OrderedConfiguration<InjectionProvider> configuration,
        @Local final ServiceOverride serviceOverride
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

    /**
     * Contributes a set of standard type coercions to the {@link TypeCoercer}
     * service:
     * <ul>
     * <li>Object to String</li>
     * <li>Object to Boolean</li>
     * <li>String to Double</li>
     * <li>String to BigDecimal</li>
     * <li>BigDecimal to Double</li>
     * <li>Double to BigDecimal</li>
     * <li>String to BigInteger</li>
     * <li>BigInteger to Long</li>
     * <li>String to Long</li>
     * <li>Long to Byte</li>
     * <li>Long to Short</li>
     * <li>Long to Integer</li>
     * <li>Double to Long</li>
     * <li>Double to Float</li>
     * <li>Float to Double</li>
     * <li>Long to Double</li>
     * <li>String to Boolean ("false" is always false, other non-blank strings are
     * true)</li>
     * <li>Number to Boolean (true if number value is non zero)</li>
     * <li>Null to Boolean (always false)</li>
     * <li>Collection to Boolean (false if empty)</li>
     * <li>Object[] to List</li>
     * <li>primitive[] to List</li>
     * <li>Object to List (by wrapping as a singleton list)</li>
     * <li>String to File</li>
     * <li>String to {@link com.jujin.freeway.ioc.schedule.TimeInterval}</li>
     * <li>{@link com.jujin.freeway.ioc.schedule.TimeInterval} to Long</li>
     * <li>Object to Object[] (wrapping the object as an array)</li>
     * <li>Collection to Object[] (via the toArray() method)
     * </ul>
     */
    @Contribute(TypeCoercer.class)
    public static void provideBasicTypeCoercions(
        MappedConfiguration<CoercionTuple.Key, CoercionTuple> configuration
    ) {
        BasicTypeCoercions.provideBasicTypeCoercions(configuration);
    }

    /**
     * Contributes coercions to and from Java Time API (JSR 310) classes.
     * <ul>
     * <li>java.time.Year to Integer</li>
     * <li>Integer to java.time.Year</li>
     * <li>java.time.Month to Integer</li>
     * <li>Integer to Java.time.Month</li>
     * <li>java.time.Month to String</li>
     * <li>String to java.time.Month</li>
     * <li>String to java.time.YearMonth</li>
     * <li>java.time.YearMonth to java.time.Year</li>
     * <li>java.time.YearMonth to java.time.Month</li>
     * <li>String to java.time.MonthDay</li>
     * <li>java.time.MonthDay to java.time.Month</li>
     * <li>java.time.DayOfWeek to Integer</li>
     * <li>Integer to java.time.DayOfWeek</li>
     * <li>java.time.DayOfWeek to String</li>
     * <li>String to java.time.DayOfWeek</li>
     * <li>java.time.LocalDate to java.time.Instant</li>
     * <li>java.time.Instant to java.time.LocalDate</li>
     * <li>String to java.time.LocalDate</li>
     * <li>java.time.LocalDate to java.time.YearMonth</li>
     * <li>java.time.LocalDate to java.time.MonthDay</li>
     * <li>java.time.LocalTime to Long</li>
     * <li>Long to java.time.LocalTime</li>
     * <li>String to java.time.LocalDateTime</li>
     * <li>java.time.LocalDateTime to java.time.Instant</li>
     * <li>java.time.Instant to LocalDateTime</li>
     * <li>java.time.LocalDateTime to java.time.LocalDate</li>
     * <li>String to java.time.OffsetDateTime</li>
     * <li>java.time.OffsetDateTime to java.time.Instant</li>
     * <li>java.time.Instant to java.time.OffsetDateTime</li>
     * <li>String to java.time.ZoneId</li>
     * <li>String to java.time.ZoneOffset</li>
     * <li>String to java.time.ZonedDateTime</li>
     * <li>java.time.ZonedDateTime to java.time.Instant</li>
     * <li>java.time.ZonedDateTime to java.time.ZoneId</li>
     * <li>java.time.Instant to Long</li>
     * <li>Long to java.time.Instant</li>
     * <li>java.time.Instant to java.util.Date</li>
     * <li>java.util.Date to java.time.Instant</li>
     * <li>java.time.Duration to Long</li>
     * <li>Long to java.time.Duration</li>
     * <li>String to java.time.Period</li>
     * </ul>
     */
    @Contribute(TypeCoercer.class)
    public static void provideJSR310TypeCoercions(
        MappedConfiguration<CoercionTuple.Key, CoercionTuple> configuration
    ) {
        BasicTypeCoercions.provideJSR310TypeCoercions(configuration);
    }

    /**
     * <dl>
     * <dt>SystemProperties</dt>
     * <dd>Exposes JVM System properties as symbols (currently case-sensitive)</dd>
     * <dt>EnvironmentVariables</dt>
     * <dd>Exposes environment variables as symbols (adding a "env." prefix)</dd>
     * <dt>ApplicationDefaults</dt>
     * <dd>Values contributed
     * to @{@link SymbolProvider} @{@link ApplicationDefaults}</dd>
     * <dt>FactoryDefaults</dt>
     * <dd>Values contributed
     * to @{@link SymbolProvider} @{@link FactoryDefaults}</dd>
     * </dl>
     */
    @Contribute(SymbolSource.class)
    public static void setupStandardSymbolProviders(
        OrderedConfiguration<SymbolProvider> configuration,
        @ApplicationDefaults SymbolProvider applicationDefaults,
        @FactoryDefaults SymbolProvider factoryDefaults
    ) {
        configuration.add(
            "SystemProperties",
            new SystemPropertiesSymbolProvider(),
            "before:*"
        );
        configuration.add(
            "EnvironmentVariables",
            new SystemEnvSymbolProvider()
        );
        configuration.add("ApplicationDefaults", applicationDefaults);
        configuration.add("FactoryDefaults", factoryDefaults);
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

    public static void contributeRegistryStartup(
        OrderedConfiguration<Runnable> configuration,
        PeriodicExecutor periodicExecutor
    ) {
        configuration.add(PeriodicExecutor.class.getSimpleName(), () ->
            periodicExecutor.init()
        );
    }
}
