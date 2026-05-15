package com.jujin.freeway.ioc.internal;

import com.jujin.freeway.ioc.*;
import com.jujin.freeway.ioc.advisor.AdvisorDefinition;
import com.jujin.freeway.ioc.advisor.OperationTracker;
import com.jujin.freeway.ioc.advisor.ServiceActivityScoreboard;
import com.jujin.freeway.ioc.advisor.ServiceAdvisor;
import com.jujin.freeway.ioc.annotations.Builtin;
import com.jujin.freeway.ioc.annotations.Local;
import com.jujin.freeway.ioc.config.*;
import com.jujin.freeway.ioc.exception.UnknownValueException;
import com.jujin.freeway.ioc.internal.util.InternalUtils;
import com.jujin.freeway.ioc.internal.util.MapInjectionResources;
import com.jujin.freeway.ioc.internal.util.OneShotLock;
import com.jujin.freeway.ioc.internal.util.Orderer;
import com.jujin.freeway.ioc.lifecycle.ObjectCreator;
import com.jujin.freeway.ioc.lifecycle.ServiceLifecycle;
import com.jujin.freeway.ioc.lifecycle.ServiceLifecycleSource;
import com.jujin.freeway.ioc.lifecycle.StartupDef;
import com.jujin.freeway.ioc.symbol.SymbolSource;
import com.jujin.freeway.ioc.threading.PerThreadManager;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.slf4j.Logger;

@SuppressWarnings({ "rawtypes", "unchecked" })
public class RegistryImpl
    implements Registry, InternalRegistry, ServiceProxyProvider
{

    private static final String SYMBOL_SOURCE_SERVICE_ID = "SymbolSource";

    private static final String REGISTRY_SHUTDOWN_HUB_SERVICE_ID =
        "RegistryShutdownHub";

    static final String PERTHREAD_MANAGER_SERVICE_ID = "PerthreadManager";

    private static final String SERVICE_ACTIVITY_SCOREBOARD_SERVICE_ID =
        "ServiceActivityScoreboard";

    /**
     * The set of marker annotations for a builtin service.
     */
    private static final Set<Class<?>> BUILTIN = new HashSet<>();

    // Split create/assign to appease generics gods
    static {
        BUILTIN.add(Builtin.class);
    }

    static final String JDK_PROXY_FACTORY_SERVICE_ID = "JdkProxyFactory";

    static final String LOGGER_SOURCE_SERVICE_ID = "LoggerSource";

    private final OneShotLock lock = new OneShotLock();

    private final OneShotLock eagerLoadLock = new OneShotLock();

    private final Map<String, Object> builtinServices = new TreeMap<>(
        String.CASE_INSENSITIVE_ORDER
    );

    private final Map<String, Class> builtinTypes = new TreeMap<>(
        String.CASE_INSENSITIVE_ORDER
    );

    private final RegistryShutdownHubImpl registryShutdownHub;

    private final LoggerSource loggerSource;

    /**
     * Map from service id to the Module that contains the service.
     */
    private final Map<String, Module> serviceIdToModule = new TreeMap<>(
        String.CASE_INSENSITIVE_ORDER
    );

    private final Map<String, ServiceLifecycle> lifecycles = new TreeMap<>(
        String.CASE_INSENSITIVE_ORDER
    );

    private final PerThreadManager perthreadManager;

    private final JdkProxyFactory proxyFactory;

    private final ServiceActivityTracker tracker;

    private final ServiceActivityTrackerImpl trackerImpl;

    private SymbolSource symbolSource;

    private final Map<Module, Set<ServiceDefinition>> moduleToServiceDefs =
        new HashMap<>();

    /**
     * From marker type to a list of marked service instances.
     */
    private final Map<Class<?>, List<ServiceDefinition>> markerToServiceDef =
        new HashMap<>();

    private final Set<ServiceDefinition> serviceDefs = new HashSet<>();

    private final OperationTracker operationTracker;

    private final TypeCoercerProxy typeCoercerProxy = new TypeCoercerProxyImpl(
        this
    );

    private final Map<
        Class<? extends Annotation>,
        Annotation
    > cachedAnnotationProxies = new ConcurrentHashMap<>();

    private final Set<Runnable> startups = new HashSet<>();

    private DelegatingServiceConfigurationListener serviceConfigurationListener;

    /**
     * Constructs the registry from a set of module definitions and other resources.
     *
     * @param moduleDefs
     *            defines the modules (and builders, decorators, etc., within)
     * @param proxyFactory
     *            used to create new proxy objects
     * @param loggerSource
     *            used to obtain Logger instances
     * @param operationTracker
     */
    public RegistryImpl(
        Collection<ModuleDefinition> moduleDefs,
        JdkProxyFactory proxyFactory,
        LoggerSource loggerSource,
        OperationTracker operationTracker
    ) {
        assert moduleDefs != null;
        assert proxyFactory != null;
        assert loggerSource != null;
        assert operationTracker != null;

        this.loggerSource = loggerSource;
        this.operationTracker = operationTracker;

        this.proxyFactory = proxyFactory;

        serviceConfigurationListener =
            new DelegatingServiceConfigurationListener(
                loggerForBuiltinService(
                    ServiceConfigurationListener.class.getSimpleName()
                )
            );

        var logger = loggerForBuiltinService(PERTHREAD_MANAGER_SERVICE_ID);

        var ptmImpl = new PerThreadManagerImpl(logger);

        perthreadManager = ptmImpl;

        trackerImpl = new ServiceActivityTrackerImpl(perthreadManager);

        tracker = trackerImpl;

        logger = loggerForBuiltinService(REGISTRY_SHUTDOWN_HUB_SERVICE_ID);

        registryShutdownHub = new RegistryShutdownHubImpl(logger);
        ptmImpl.registerForShutdown(registryShutdownHub);

        lifecycles.put("singleton", new SingletonServiceLifecycle());

        registryShutdownHub.addRegistryShutdownListener(
            (Runnable) () -> trackerImpl.shutdown()
        );

        for (ModuleDefinition def : moduleDefs) {
            logger = this.loggerSource.getLogger(def.getLoggerName());

            var module = new ModuleImpl(
                this,
                tracker,
                def,
                proxyFactory,
                logger
            );

            var moduleServiceDefs = new HashSet<ServiceDefinition>();

            for (String serviceId : def.getServiceIds()) {
                var serviceDef = module.getServiceDef(serviceId);

                moduleServiceDefs.add(serviceDef);
                serviceDefs.add(serviceDef);

                var existing = serviceIdToModule.get(serviceId);

                if (existing != null) throw new RuntimeException(
                    IOCMessages.serviceIdConflict(
                        serviceId,
                        existing.getServiceDef(serviceId),
                        serviceDef
                    )
                );

                serviceIdToModule.put(serviceId, module);

                // The service is defined but will not have gone further than that.
                tracker.define(serviceDef, ServiceStatus.DEFINED);

                for (Class<?> marker : serviceDef.getMarkers())
                    InternalUtils.addToMapList(
                        markerToServiceDef,
                        marker,
                        serviceDef
                    );
            }

            moduleToServiceDefs.put(module, moduleServiceDefs);

            addStartupsInModule(def, module, logger);
        }

        addBuiltin(
            SERVICE_ACTIVITY_SCOREBOARD_SERVICE_ID,
            ServiceActivityScoreboard.class,
            trackerImpl
        );
        addBuiltin(
            LOGGER_SOURCE_SERVICE_ID,
            LoggerSource.class,
            this.loggerSource
        );
        addBuiltin(
            PERTHREAD_MANAGER_SERVICE_ID,
            PerThreadManager.class,
            perthreadManager
        );
        addBuiltin(
            REGISTRY_SHUTDOWN_HUB_SERVICE_ID,
            RegistryShutdownHub.class,
            registryShutdownHub
        );
        addBuiltin(
            JDK_PROXY_FACTORY_SERVICE_ID,
            JdkProxyFactory.class,
            proxyFactory
        );

        validateContributeDefs(moduleDefs);

        serviceConfigurationListener.setDelegates(
            getService(ServiceConfigurationListenerHub.class).getListeners()
        );

        SerializationSupport.setProvider(this);
    }

    private void addStartupsInModule(
        ModuleDefinition def,
        final Module module,
        final Logger logger
    ) {
        for (final StartupDef startup : def.getStartups()) {
            startups.add(() ->
                startup.invoke(
                    module,
                    RegistryImpl.this,
                    RegistryImpl.this,
                    logger
                )
            );
        }
    }

    /**
     * Validate that each module's ContributeDefs correspond to an actual service.
     */
    private void validateContributeDefs(
        Collection<ModuleDefinition> moduleDefs
    ) {
        for (ModuleDefinition module : moduleDefs) {
            var contributionDefs = module.getContributionDefs();

            for (ContributionDef cd : contributionDefs) {
                String serviceId = cd.getServiceId();

                // Ignore any optional contribution methods; there's no way to validate that
                // they contribute to a known service ... that's the point of @Optional

                if (cd.isOptional()) {
                    continue;
                }

                // Otherwise, check that the service being contributed to exists ...

                if (cd.getServiceId() != null) {
                    if (!serviceIdToModule.containsKey(serviceId)) {
                        throw new IllegalArgumentException(
                            IOCMessages.contributionForNonexistentService(cd)
                        );
                    }
                } else if (!isContributionForExistentService(module, cd)) {
                    throw new IllegalArgumentException(
                        IOCMessages.contributionForUnqualifiedService(cd)
                    );
                }
            }
        }
    }

    /**
     * Invoked when the contribution method didn't follow the naming convention and
     * so doesn't identify a service by id; instead there was an @Contribute to
     * identify the service interface.
     */
    private boolean isContributionForExistentService(
        ModuleDefinition moduleDef,
        final ContributionDef cd
    ) {
        var contributionMarkers = new HashSet<>(cd.getMarkers());

        boolean localOnly = contributionMarkers.contains(Local.class);

        Stream<ServiceDefinition> serviceDefs = localOnly
            ? getLocalServiceDefs(moduleDef)
            : this.serviceDefs.stream();

        contributionMarkers.retainAll(getMarkerAnnotations());
        contributionMarkers.remove(Local.class);

        // Match services with the correct interface AND having as markers *all* the
        // marker annotations.
        // anyMatch will short-circuit as soon as it finds a single match.

        return serviceDefs.anyMatch(
            sd ->
                sd.getServiceInterface().equals(cd.getServiceInterface()) &&
                sd.getMarkers().containsAll(contributionMarkers)
        );
    }

    private Stream<ServiceDefinition> getLocalServiceDefs(
        final ModuleDefinition moduleDef
    ) {
        return moduleDef
            .getServiceIds()
            .stream()
            .map(value -> moduleDef.getServiceDef(value));
    }

    /**
     * It's not unreasonable for an eagerly-loaded service to decide to start a
     * thread, at which point we raise issues about improper publishing of the
     * Registry instance from the RegistryImpl constructor. Moving eager loading of
     * services out to its own method should ensure thread safety.
     */
    @Override
    public void performRegistryStartup() {
        eagerLoadLock.lock();

        trackerImpl.startup();

        List<EagerLoadServiceProxy> proxies = new ArrayList<>();

        for (Module m : moduleToServiceDefs.keySet())
            m.collectEagerLoadServices(proxies);

        // TAPESTRY-2267: Gather up all the proxies before instantiating any of them.

        for (EagerLoadServiceProxy proxy : proxies) {
            proxy.eagerLoadService();
        }

        for (Runnable startup : startups) {
            startup.run();
        }

        startups.clear();

        getService("RegistryStartup", Runnable.class).run();

        cleanupThread();
    }

    @Override
    public Logger getServiceLogger(String serviceId) {
        var module = serviceIdToModule.get(serviceId);

        assert module != null;

        return loggerSource.getLogger(module.getLoggerName() + "." + serviceId);
    }

    private Logger loggerForBuiltinService(String serviceId) {
        return loggerSource.getLogger(
            FreewayIOCModule.class.getName() + "." + serviceId
        );
    }

    private <T> void addBuiltin(
        final String serviceId,
        final Class<T> serviceInterface,
        T service
    ) {
        builtinTypes.put(serviceId, serviceInterface);
        builtinServices.put(serviceId, service);

        // Make sure each of the builtin services is also available via the Builtin
        // annotation
        // marker.

        ServiceDefinition serviceDef = new ServiceDefinition() {
            @Override
            public ObjectCreator createServiceCreator(
                ServiceBuilderResources resources
            ) {
                return null;
            }

            @Override
            public Set<Class<?>> getMarkers() {
                return BUILTIN;
            }

            @Override
            public String getServiceId() {
                return serviceId;
            }

            @Override
            public Class<?> getServiceInterface() {
                return serviceInterface;
            }

            @Override
            public String getServiceScope() {
                return InternalUtils.DEFAULT;
            }

            @Override
            public boolean isEagerLoad() {
                return false;
            }

            @Override
            public boolean isPreventDecoration() {
                return true;
            }

            @Override
            public AnnotationProvider getClassAnnotationProvider() {
                return InternalUtils.toAnnotationProvider(
                    getServiceInterface()
                );
            }

            @Override
            @SuppressWarnings("rawtypes")
            public AnnotationProvider getMethodAnnotationProvider(
                String methodName,
                Class... argumentTypes
            ) {
                return InternalUtils.toAnnotationProvider(
                    InternalUtils.findMethod(
                        getServiceInterface(),
                        methodName,
                        argumentTypes
                    )
                );
            }

            @Override
            public int hashCode() {
                final int prime = 31;
                int result = 1;
                result =
                    prime * result +
                    ((serviceId == null) ? 0 : serviceId.hashCode());
                return result;
            }

            @Override
            public boolean equals(Object obj) {
                if (this == obj) {
                    return true;
                }
                if (obj == null) {
                    return false;
                }
                if (!(obj instanceof ServiceDefinitionImpl)) {
                    return false;
                }
                ServiceDefinition other = (ServiceDefinition) obj;
                if (serviceId == null) {
                    if (other.getServiceId() != null) {
                        return false;
                    }
                } else if (!serviceId.equals(other.getServiceId())) {
                    return false;
                }
                return true;
            }
        };

        for (Class<?> marker : serviceDef.getMarkers()) {
            InternalUtils.addToMapList(markerToServiceDef, marker, serviceDef);
            serviceDefs.add(serviceDef);
        }

        tracker.define(serviceDef, ServiceStatus.BUILTIN);
    }

    @Override
    public synchronized void shutdown() {
        lock.lock();

        registryShutdownHub.fireRegistryDidShutdown();

        SerializationSupport.clearProvider(this);
    }

    @Override
    public <T> T getService(String serviceId, Class<T> serviceInterface) {
        lock.check();

        serviceId = expand(serviceId);

        var result = checkForBuiltinService(serviceId, serviceInterface);
        if (result != null) return result;

        // Checking serviceId and serviceInterface is overkill; they have been checked
        // and rechecked
        // all the way to here.

        var containingModule = locateModuleForService(serviceId);

        return containingModule.getService(serviceId, serviceInterface);
    }

    private <T> T checkForBuiltinService(
        String serviceId,
        Class<T> serviceInterface
    ) {
        var service = builtinServices.get(serviceId);

        if (service == null) return null;

        try {
            return serviceInterface.cast(service);
        } catch (ClassCastException ex) {
            throw new RuntimeException(
                IOCMessages.serviceWrongInterface(
                    serviceId,
                    builtinTypes.get(serviceId),
                    serviceInterface
                )
            );
        }
    }

    @Override
    public void cleanupThread() {
        lock.check();

        perthreadManager.cleanup();
    }

    private Module locateModuleForService(String serviceId) {
        var module = serviceIdToModule.get(serviceId);

        if (module == null) throw new UnknownValueException(
            String.format(
                "Service id '%s' is not defined by any module.",
                serviceId
            ),
            serviceIdToModule
                .keySet()
                .stream()
                .map(Object::toString)
                .sorted()
                .toList()
        );

        return module;
    }

    @Override
    public <T> Collection<T> getUnorderedConfiguration(
        ServiceDefinition serviceDef,
        Class<T> objectType
    ) {
        lock.check();

        final Collection<T> result = new ArrayList<>();

        // . NOTICE: if someday an ordering between modules is added, this should be
        // reverted
        // or a notice added to the documentation.
        var modules = new ArrayList<Module>(moduleToServiceDefs.keySet());
        Collections.sort(modules, new ModuleComparator());

        for (Module m : modules)
            addToUnorderedConfiguration(result, objectType, serviceDef, m);

        if (!isServiceConfigurationListenerServiceDef(serviceDef)) {
            serviceConfigurationListener.onUnorderedConfiguration(
                serviceDef,
                result
            );
        }

        return result;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> List<T> getOrderedConfiguration(
        ServiceDefinition serviceDef,
        Class<T> objectType
    ) {
        lock.check();

        String serviceId = serviceDef.getServiceId();
        var logger = getServiceLogger(serviceId);

        var orderer = new Orderer<T>(logger);
        var overrides = new TreeMap<String, OrderedConfigurationOverride<T>>(
            String.CASE_INSENSITIVE_ORDER
        );

        // . NOTICE: if someday an ordering between modules is added, this should be
        // reverted
        // or a notice added to the documentation.
        var modules = new ArrayList<Module>(moduleToServiceDefs.keySet());
        Collections.sort(modules, new ModuleComparator());

        for (Module m : modules)
            addToOrderedConfiguration(
                orderer,
                overrides,
                objectType,
                serviceDef,
                m
            );

        // An ugly hack ... perhaps we should introduce a new builtin service so that
        // this can be
        // accomplished in the normal way?

        if (serviceId.equals("ObjectInjector")) {
            ServiceProvider contribution = new ServiceProvider() {
                @Override
                public <T> T resolve(
                    Class<T> objectType,
                    AnnotationProvider annotationProvider,
                    ServiceLocator locator
                ) {
                    return findServiceByMarkerAndType(
                        objectType,
                        annotationProvider,
                        null
                    );
                }
            };

            orderer.add("ServiceByMarker", (T) contribution);
        }

        for (OrderedConfigurationOverride<T> override : overrides.values())
            override.apply();

        var result = orderer.getOrdered();

        if (!isServiceConfigurationListenerServiceDef(serviceDef)) {
            serviceConfigurationListener.onOrderedConfiguration(
                serviceDef,
                result
            );
        }

        return result;
    }

    private boolean isServiceConfigurationListenerServiceDef(
        ServiceDefinition serviceDef
    ) {
        return serviceDef
            .getServiceId()
            .equalsIgnoreCase(
                ServiceConfigurationListener.class.getSimpleName()
            );
    }

    @Override
    public <K, V> Map<K, V> getMappedConfiguration(
        ServiceDefinition serviceDef,
        Class<K> keyType,
        Class<V> objectType
    ) {
        lock.check();

        // When the key type is String, then a case insensitive map is used.

        Map<K, V> result = newConfigurationMap(keyType);
        Map<K, ContributionDef> keyToContribution = newConfigurationMap(
            keyType
        );
        Map<K, MappedConfigurationOverride<K, V>> overrides =
            newConfigurationMap(keyType);

        for (Module m : moduleToServiceDefs.keySet())
            addToMappedConfiguration(
                result,
                overrides,
                keyToContribution,
                keyType,
                objectType,
                serviceDef,
                m
            );

        for (MappedConfigurationOverride<K, V> override : overrides.values()) {
            override.apply();
        }

        if (!isServiceConfigurationListenerServiceDef(serviceDef)) {
            serviceConfigurationListener.onMappedConfiguration(
                serviceDef,
                result
            );
        }

        return result;
    }

    @SuppressWarnings("unchecked")
    private <K, V> Map<K, V> newConfigurationMap(Class<K> keyType) {
        if (keyType.equals(String.class)) {
            Map<String, K> result = new TreeMap<>(
                String.CASE_INSENSITIVE_ORDER
            );

            return (Map<K, V>) result;
        }

        return new HashMap<>();
    }

    private <K, V> void addToMappedConfiguration(
        Map<K, V> map,
        Map<K, MappedConfigurationOverride<K, V>> overrides,
        Map<K, ContributionDef> keyToContribution,
        Class<K> keyClass,
        Class<V> valueType,
        ServiceDefinition serviceDef,
        final Module module
    ) {
        String serviceId = serviceDef.getServiceId();
        Set<ContributionDef> contributions =
            module.getContributorDefsForService(serviceDef);

        if (contributions.isEmpty()) return;

        Logger logger = getServiceLogger(serviceId);

        var resources = new ServiceResourcesImpl(
            this,
            module,
            serviceDef,
            proxyFactory,
            logger
        );

        for (final ContributionDef def : contributions) {
            final MappedConfiguration<K, V> validating =
                new ValidatingMappedConfigurationWrapper<K, V>(
                    valueType,
                    resources,
                    typeCoercerProxy,
                    map,
                    overrides,
                    serviceId,
                    def,
                    keyClass,
                    keyToContribution
                );

            String description = "Invoking " + def;

            logger.debug(description);

            operationTracker.run(description, () ->
                def.contribute(module, resources, validating)
            );
        }
    }

    private <T> void addToUnorderedConfiguration(
        Collection<T> collection,
        Class<T> valueType,
        ServiceDefinition serviceDef,
        final Module module
    ) {
        String serviceId = serviceDef.getServiceId();
        Set<ContributionDef> contributions =
            module.getContributorDefsForService(serviceDef);

        if (contributions.isEmpty()) return;

        Logger logger = getServiceLogger(serviceId);

        var resources = new ServiceResourcesImpl(
            this,
            module,
            serviceDef,
            proxyFactory,
            logger
        );

        for (final ContributionDef def : contributions) {
            final Configuration<T> validating =
                new ValidatingConfigurationWrapper<T>(
                    valueType,
                    resources,
                    typeCoercerProxy,
                    collection,
                    serviceId
                );

            String description = "Invoking " + def;

            logger.debug(description);

            operationTracker.run(description, () ->
                def.contribute(module, resources, validating)
            );
        }
    }

    private <T> void addToOrderedConfiguration(
        Orderer<T> orderer,
        Map<String, OrderedConfigurationOverride<T>> overrides,
        Class<T> valueType,
        ServiceDefinition serviceDef,
        final Module module
    ) {
        String serviceId = serviceDef.getServiceId();
        Set<ContributionDef> contributions =
            module.getContributorDefsForService(serviceDef);

        if (contributions.isEmpty()) return;

        Logger logger = getServiceLogger(serviceId);

        var resources = new ServiceResourcesImpl(
            this,
            module,
            serviceDef,
            proxyFactory,
            logger
        );

        for (final ContributionDef def : contributions) {
            final OrderedConfiguration<T> validating =
                new ValidatingOrderedConfigurationWrapper<T>(
                    valueType,
                    resources,
                    typeCoercerProxy,
                    orderer,
                    overrides,
                    def
                );

            String description = "Invoking " + def;

            logger.debug(description);

            operationTracker.run(description, () ->
                def.contribute(module, resources, validating)
            );
        }
    }

    @Override
    public <T> T getService(Class<T> serviceInterface) {
        lock.check();

        return getServiceByTypeAndMarkers(serviceInterface);
    }

    @Override
    public <T> T getService(
        Class<T> serviceInterface,
        Class<? extends Annotation>... markerTypes
    ) {
        lock.check();

        return getServiceByTypeAndMarkers(serviceInterface, markerTypes);
    }

    @Override
    public <T> Collection<T> getServices(Class<T> serviceInterface) {
        lock.check();

        var serviceIds = findServiceIdsForInterface(serviceInterface);
        if (serviceIds == null || serviceIds.isEmpty()) return List.of();

        List<T> result = new ArrayList<>(serviceIds.size());
        for (String id : serviceIds) {
            result.add(getService(id, serviceInterface));
        }
        return Collections.unmodifiableCollection(result);
    }

    private <T> T getServiceByTypeAlone(Class<T> serviceInterface) {
        var serviceIds = findServiceIdsForInterface(serviceInterface);

        if (serviceIds == null) serviceIds = Collections.emptyList();

        switch (serviceIds.size()) {
            case 0:
                throw new RuntimeException(
                    IOCMessages.noServiceMatchesType(serviceInterface)
                );
            case 1:
                String serviceId = serviceIds.get(0);

                return getService(serviceId, serviceInterface);
            default:
                Collections.sort(serviceIds);

                // Check for @Primary: if exactly one service is marked @Primary, use it
                String primaryId = findPrimaryServiceId(serviceIds);
                if (primaryId != null) {
                    return getService(primaryId, serviceInterface);
                }

                throw new RuntimeException(
                    IOCMessages.manyServiceMatches(serviceInterface, serviceIds)
                );
        }
    }

    /**
     * Among the given service ids, find the one marked with {@code @Primary}.
     * Returns null if zero or 2+ match.
     */
    private String findPrimaryServiceId(List<String> serviceIds) {
        String found = null;
        for (String id : serviceIds) {
            Module m = serviceIdToModule.get(id);
            if (m != null) {
                var def = m.getServiceDef(id);
                if (
                    def != null &&
                    def
                        .getMarkers()
                        .contains(
                            com.jujin.freeway.ioc.annotations.Primary.class
                        )
                ) {
                    if (found != null) {
                        // Multiple @Primary services — ambiguous
                        return null;
                    }
                    found = id;
                }
            }
        }
        return found;
    }

    private <T> T getServiceByTypeAndMarkers(
        Class<T> serviceInterface,
        Class<? extends Annotation>... markerTypes
    ) {
        if (markerTypes.length == 0) {
            return getServiceByTypeAlone(serviceInterface);
        }

        var provider = createAnnotationProvider(markerTypes);

        Set<ServiceDefinition> matches = new HashSet<>();
        List<Class<?>> markers = new ArrayList<>();

        findServiceDefsMatchingMarkerAndType(
            serviceInterface,
            provider,
            null,
            markers,
            matches
        );

        return extractServiceFromMatches(serviceInterface, markers, matches);
    }

    private AnnotationProvider createAnnotationProvider(
        Class<? extends Annotation>... markerTypes
    ) {
        var map = new HashMap<Class<? extends Annotation>, Annotation>();

        for (Class<? extends Annotation> markerType : markerTypes) {
            map.put(markerType, createAnnotationProxy(markerType));
        }

        return new AnnotationProvider() {
            @Override
            public <T extends Annotation> T getAnnotation(
                Class<T> annotationClass
            ) {
                return annotationClass.cast(map.get(annotationClass));
            }
        };
    }

    private <A extends Annotation> Annotation createAnnotationProxy(
        final Class<A> annotationType
    ) {
        Annotation result = cachedAnnotationProxies.get(annotationType);

        if (result == null) {
            // We create a JDK proxy because its pretty quick and easy.

            InvocationHandler handler = new InvocationHandler() {
                @Override
                public Object invoke(Object proxy, Method method, Object[] args)
                    throws Throwable {
                    String methodName = method.getName();
                    
                    // Explicitly handle standard annotation methods
                    if (methodName.equals("annotationType")) {
                        return annotationType;
                    }
                    
                    if (methodName.equals("toString")) {
                        return "@" + annotationType.getName();
                    }
                    
                    if (methodName.equals("hashCode")) {
                        return System.identityHashCode(proxy);
                    }
                    
                    if (methodName.equals("equals")) {
                        // For marker annotation proxies, equality is based on reference identity
                        // since we cache one proxy per annotation type
                        return proxy == args[0];
                    }
                    
                    // Any other method invocation is not supported
                    throw new UnsupportedOperationException(
                        String.format("Method '%s' is not supported on marker annotation proxy for %s",
                            methodName, annotationType.getName()));
                }
            };

            result = (Annotation) Proxy.newProxyInstance(
                Thread.currentThread().getContextClassLoader(),
                new Class[] { annotationType },
                handler
            );

            cachedAnnotationProxies.put(annotationType, result);
        }

        return result;
    }

    private List<String> findServiceIdsForInterface(Class<?> serviceInterface) {
        List<String> result = new ArrayList<>();

        for (Module module : moduleToServiceDefs.keySet())
            result.addAll(module.findServiceIdsForInterface(serviceInterface));

        for (Map.Entry<String, Object> entry : builtinServices.entrySet()) {
            if (serviceInterface.isInstance(entry.getValue())) result.add(
                entry.getKey()
            );
        }

        Collections.sort(result);

        return result;
    }

    @Override
    public ServiceLifecycle getServiceLifecycle(String scope) {
        lock.check();

        ServiceLifecycle result = lifecycles.get(scope);

        if (result == null) {
            var source = getService(
                "ServiceLifecycleSource",
                ServiceLifecycleSource.class
            );

            result = source.get(scope);
        }

        if (result == null) throw new RuntimeException(
            IOCMessages.unknownScope(scope)
        );

        return result;
    }

    @Override
    public List<ServiceAdvisor> findAdvisorsForService(
        ServiceDefinition serviceDef
    ) {
        lock.check();

        assert serviceDef != null;

        var logger = getServiceLogger(serviceDef.getServiceId());

        var orderer = new Orderer<ServiceAdvisor>(logger, true);

        for (Module module : moduleToServiceDefs.keySet()) {
            var advisorDefs = module.findMatchingServiceAdvisors(serviceDef);

            if (advisorDefs.isEmpty()) continue;

            var resources = new ServiceResourcesImpl(
                this,
                module,
                serviceDef,
                proxyFactory,
                logger
            );

            for (AdvisorDefinition def : advisorDefs) {
                var advisor = def.createAdvisor(module, resources);

                orderer.add(def.getAdvisorId(), advisor, def.getConstraints());
            }
        }

        return orderer.getOrdered();
    }

    @Override
    public <T> T getObject(
        Class<T> objectType,
        AnnotationProvider annotationProvider,
        ServiceLocator locator,
        Module localModule
    ) {
        lock.check();

        AnnotationProvider effectiveProvider =
            annotationProvider != null
                ? annotationProvider
                : new NullAnnotationProvider();

        // We do a check here for known marker/type combinations, so that you can use a
        // marker
        // annotation
        // to inject into a contribution method that contributes to ObjectInjector.
        // We also force a contribution into ObjectInjector to accomplish the same
        // thing.

        var result = findServiceByMarkerAndType(
            objectType,
            annotationProvider,
            localModule
        );

        if (result != null) return result;

        var injector = getService(
            InternalUtils.INJECTOR_SERVICE_ID,
            ObjectInjector.class
        );

        return injector.inject(objectType, effectiveProvider, locator, true);
    }

    private Collection<ServiceDefinition> filterByType(
        Class<?> objectType,
        Collection<ServiceDefinition> serviceDefs
    ) {
        Collection<ServiceDefinition> result = new HashSet<>();

        for (ServiceDefinition sd : serviceDefs) {
            if (objectType.isAssignableFrom(sd.getServiceInterface())) {
                result.add(sd);
            }
        }

        return result;
    }

    @SuppressWarnings("unchecked")
    private <T> T findServiceByMarkerAndType(
        Class<T> objectType,
        AnnotationProvider provider,
        Module localModule
    ) {
        if (provider == null) return null;

        Set<ServiceDefinition> matches = new HashSet<>();
        List<Class<?>> markers = new ArrayList<>();

        findServiceDefsMatchingMarkerAndType(
            objectType,
            provider,
            localModule,
            markers,
            matches
        );

        // If didn't see @Local or any recognized marker annotation, then don't try to
        // filter that
        // way. Continue on, eventually to the ObjectInjector service.

        if (markers.isEmpty()) {
            return null;
        }

        return extractServiceFromMatches(objectType, markers, matches);
    }

    /**
     * Given markers and matches processed by
     * {@link #findServiceDefsMatchingMarkerAndType(Class, com.jujin.freeway.ioc.AnnotationProvider, Module, java.util.List, java.util.Set)},
     * this finds the singular match, or reports an error for 0 or 2+ matches.
     */
    private <T> T extractServiceFromMatches(
        Class<T> objectType,
        List<Class<?>> markers,
        Set<ServiceDefinition> matches
    ) {
        switch (matches.size()) {
            case 1:
                var def = matches.iterator().next();

                return getService(def.getServiceId(), objectType);
            case 0:
                // It's no accident that the user put the marker annotation at the injection
                // point, since it matches a known marker annotation, it better be there for
                // a reason. So if we don't get a match, we have to assume the user expected
                // one, and that is an error.

                // This doesn't help when the user places an annotation they *think* is a marker
                // but isn't really a marker (because no service is marked by the annotation).

                throw new RuntimeException(
                    IOCMessages.noServicesMatchMarker(objectType, markers)
                );
            default:
                throw new RuntimeException(
                    IOCMessages.manyServicesMatchMarker(
                        objectType,
                        markers,
                        matches
                    )
                );
        }
    }

    private <T> void findServiceDefsMatchingMarkerAndType(
        Class<T> objectType,
        AnnotationProvider provider,
        Module localModule,
        List<Class<?>> markers,
        Set<ServiceDefinition> matches
    ) {
        assert provider != null;

        boolean localOnly =
            localModule != null && provider.getAnnotation(Local.class) != null;

        matches.addAll(
            filterByType(
                objectType,
                localOnly ? moduleToServiceDefs.get(localModule) : serviceDefs
            )
        );

        if (localOnly) {
            markers.add(Local.class);
        }

        for (Entry<
            Class<?>,
            List<ServiceDefinition>
        > entry : markerToServiceDef.entrySet()) {
            Class<?> marker = entry.getKey();
            if (provider.getAnnotation((Class) marker) == null) {
                continue;
            }

            markers.add(marker);

            matches.retainAll(entry.getValue());

            if (matches.isEmpty()) {
                return;
            }
        }
    }

    @Override
    public <T> T getObject(
        Class<T> objectType,
        AnnotationProvider annotationProvider
    ) {
        return getObject(objectType, annotationProvider, this, null);
    }

    @Override
    public void addRegistryShutdownListener(Runnable listener) {
        lock.check();

        registryShutdownHub.addRegistryShutdownListener(listener);
    }

    @Override
    public void addRegistryWillShutdownListener(Runnable listener) {
        lock.check();

        registryShutdownHub.addRegistryWillShutdownListener(listener);
    }

    @Override
    public String expand(String input) {
        lock.check();

        // Again, a bit of work to avoid instantiating the SymbolSource until absolutely
        // necessary.

        if (!InternalUtils.containsSymbols(input)) return input;

        return getSymbolSource().expand(input);
    }

    /**
     * Defers obtaining the symbol source until actually needed.
     */
    private SymbolSource getSymbolSource() {
        if (symbolSource == null) symbolSource = getService(
            SYMBOL_SOURCE_SERVICE_ID,
            SymbolSource.class
        );

        return symbolSource;
    }

    @Override
    public <T> T autobuild(String description, final Class<T> clazz) {
        return invoke(description, () -> autobuild(clazz));
    }

    @Override
    public <T> T autobuild(final Class<T> clazz) {
        assert clazz != null;
        final Constructor constructor = InternalUtils.findAutobuildConstructor(
            clazz
        );

        if (constructor == null) {
            throw new RuntimeException(
                IOCMessages.noAutobuildConstructor(clazz)
            );
        }

        Map<Class<?>, Object> resourcesMap = new HashMap<>();
        resourcesMap.put(OperationTracker.class, RegistryImpl.this);

        var resources = new MapInjectionResources(resourcesMap);

        ObjectCreator<T> plan = InternalUtils.createConstructorConstructionPlan(
            this,
            this,
            resources,
            null,
            "Invoking " + constructor.getName(),
            constructor
        );

        return plan.create();
    }

    @Override
    public <T> T proxy(
        Class<T> interfaceClass,
        Class<? extends T> implementationClass
    ) {
        return proxy(interfaceClass, implementationClass, this);
    }

    @Override
    public <T> T proxy(
        Class<T> interfaceClass,
        Class<? extends T> implementationClass,
        ServiceLocator locator
    ) {
        assert interfaceClass != null;
        assert implementationClass != null;

        if (
            InternalUtils.SERVICE_CLASS_RELOADING_ENABLED &&
            InternalUtils.isLocalFile(implementationClass)
        ) return createReloadingProxy(
            interfaceClass,
            implementationClass,
            locator
        );

        return createNonReloadingProxy(
            interfaceClass,
            implementationClass,
            locator
        );
    }

    private <T> T createNonReloadingProxy(
        Class<T> interfaceClass,
        final Class<? extends T> implementationClass,
        final ServiceLocator locator
    ) {
        final ObjectCreator<T> autobuildCreator = () ->
            locator.autobuild(implementationClass);

        ObjectCreator<T> justInTime = new ObjectCreator<T>() {
            private T delegate;

            @Override
            public synchronized T create() {
                if (delegate == null) delegate = autobuildCreator.create();

                return delegate;
            }
        };

        return proxyFactory.createProxy(
            interfaceClass,
            justInTime,
            String.format(
                "<Autobuild proxy %s(%s)>",
                implementationClass.getName(),
                interfaceClass.getName()
            )
        );
    }

    private <T> T createReloadingProxy(
        Class<T> interfaceClass,
        final Class<? extends T> implementationClass,
        ServiceLocator locator
    ) {
        var creator = new ReloadableObjectCreator(
            proxyFactory,
            implementationClass.getClassLoader(),
            implementationClass.getName(),
            loggerSource.getLogger(implementationClass),
            this,
            locator
        );

        getService(UpdateListenerHub.class).addUpdateListener(creator);

        return proxyFactory.createProxy(
            interfaceClass,
            (ObjectCreator<T>) creator,
            String.format(
                "<Autoreload proxy %s(%s)>",
                implementationClass.getName(),
                interfaceClass.getName()
            )
        );
    }

    @Override
    public Object provideServiceProxy(String serviceId) {
        return getService(serviceId, Object.class);
    }

    @Override
    public void run(String description, Runnable operation) {
        operationTracker.run(description, operation);
    }

    @Override
    public <T> T invoke(String description, Supplier<T> operation) {
        return operationTracker.invoke(description, operation);
    }

    @Override
    public <T> T perform(String description, IOOperation<T> operation)
        throws IOException {
        return operationTracker.perform(description, operation);
    }

    @Override
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public Set<Class<?>> getMarkerAnnotations() {
        return (Set) markerToServiceDef.keySet();
    }

    private static final class ModuleComparator implements Comparator<Module> {

        @Override
        public int compare(Module m1, Module m2) {
            return m1.getLoggerName().compareTo(m2.getLoggerName());
        }
    }

    private static final class DelegatingServiceConfigurationListener
        implements ServiceConfigurationListener
    {

        private final Logger logger;

        private List<ServiceConfigurationListener> delegates;
        private Map<ServiceDefinition, Map> mapped = new HashMap<>();
        private Map<ServiceDefinition, Collection> unordered = new HashMap<>();
        private Map<ServiceDefinition, List> ordered = new HashMap<>();

        public DelegatingServiceConfigurationListener(Logger logger) {
            this.logger = logger;
        }

        public void setDelegates(List<ServiceConfigurationListener> delegates) {
            this.delegates = delegates;

            for (Entry<ServiceDefinition, Map> entry : mapped.entrySet()) {
                for (ServiceConfigurationListener delegate : delegates) {
                    delegate.onMappedConfiguration(
                        entry.getKey(),
                        Collections.unmodifiableMap(entry.getValue())
                    );
                }
            }

            for (Entry<
                ServiceDefinition,
                Collection
            > entry : unordered.entrySet()) {
                for (ServiceConfigurationListener delegate : delegates) {
                    delegate.onUnorderedConfiguration(
                        entry.getKey(),
                        Collections.unmodifiableCollection(entry.getValue())
                    );
                }
            }

            for (Entry<ServiceDefinition, List> entry : ordered.entrySet()) {
                for (ServiceConfigurationListener delegate : delegates) {
                    delegate.onOrderedConfiguration(
                        entry.getKey(),
                        Collections.unmodifiableList(entry.getValue())
                    );
                }
            }

            mapped.clear();
            mapped = null;
            unordered.clear();
            unordered = null;
            ordered.clear();
            ordered = null;
        }

        @Override
        public void onOrderedConfiguration(
            ServiceDefinition serviceDef,
            List configuration
        ) {
            log("ordered", serviceDef, configuration);
            if (delegates == null) {
                ordered.put(serviceDef, configuration);
            } else {
                for (ServiceConfigurationListener delegate : delegates) {
                    delegate.onOrderedConfiguration(
                        serviceDef,
                        Collections.unmodifiableList(configuration)
                    );
                }
            }
        }

        @Override
        public void onUnorderedConfiguration(
            ServiceDefinition serviceDef,
            Collection configuration
        ) {
            log("unordered", serviceDef, configuration);
            if (delegates == null) {
                unordered.put(serviceDef, configuration);
            } else {
                for (ServiceConfigurationListener delegate : delegates) {
                    delegate.onUnorderedConfiguration(
                        serviceDef,
                        Collections.unmodifiableCollection(configuration)
                    );
                }
            }
        }

        @Override
        public void onMappedConfiguration(
            ServiceDefinition serviceDef,
            Map configuration
        ) {
            log("mapped", serviceDef, configuration);
            if (delegates == null) {
                mapped.put(serviceDef, configuration);
            } else {
                for (ServiceConfigurationListener delegate : delegates) {
                    delegate.onMappedConfiguration(
                        serviceDef,
                        Collections.unmodifiableMap(configuration)
                    );
                }
            }
        }

        private void log(
            String type,
            ServiceDefinition serviceDef,
            Object configuration
        ) {
            if (logger.isDebugEnabled()) {
                logger.debug(
                    "Service {} {} configuration: {}",
                    serviceDef.getServiceId(),
                    type,
                    configuration.toString()
                );
            }
        }
    }
}
