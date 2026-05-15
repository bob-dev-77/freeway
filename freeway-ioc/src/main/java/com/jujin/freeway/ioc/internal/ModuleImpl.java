package com.jujin.freeway.ioc.internal;

import com.jujin.freeway.ioc.*;
import com.jujin.freeway.ioc.advisor.AdvisorDefinition;
import com.jujin.freeway.ioc.advisor.AspectInterceptor;
import com.jujin.freeway.ioc.advisor.OperationTracker;
import com.jujin.freeway.ioc.annotations.Local;
import com.jujin.freeway.ioc.config.ContributionDef;
import com.jujin.freeway.ioc.internal.util.ConcurrentBarrier;
import com.jujin.freeway.ioc.internal.util.InjectionResources;
import com.jujin.freeway.ioc.internal.util.InternalUtils;
import com.jujin.freeway.ioc.internal.util.MapInjectionResources;
import com.jujin.freeway.ioc.lifecycle.ObjectCreator;
import com.jujin.freeway.ioc.lifecycle.ServiceLifecycle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static java.lang.String.format;

@SuppressWarnings({ "rawtypes", "unchecked" })
public class ModuleImpl implements Module {

    private final InternalRegistry registry;

    private final ServiceActivityTracker tracker;

    private final ModuleDefinition moduleDef;

    private final JdkProxyFactory proxyFactory;

    private final Logger logger;

    // For JDK 25+, we can directly use Class.isSealed()
    // A class can be proxied if it's an interface and not sealed
    private static final Predicate<Class<?>> canBeProxiedPredicate = 
        c -> c.isInterface() && !c.isSealed();

    /**
     * Lazily instantiated. Access is guarded by BARRIER.
     */
    private Object moduleInstance;

    // Set to true when invoking the module constructor. Used to
    // detect endless loops caused by irresponsible dependencies in
    // the constructor.
    private boolean insideConstructor;

    /**
     * Keyed on fully qualified service id; values are instantiated services
     * (proxies). Guarded by BARRIER.
     */
    private final Map<String, Object> services = new ConcurrentSkipListMap<>(
        String.CASE_INSENSITIVE_ORDER);

    /**
     * EagerLoadProxies collection into which proxies for eager loaded services are
     * added. Guarded by BARRIER
     */
    private final Collection<EagerLoadServiceProxy> eagerLoadProxies = new ArrayList<>();

    private final Map<String, ServiceDefinition> serviceDefs = new ConcurrentSkipListMap<>(
        String.CASE_INSENSITIVE_ORDER);

    /**
     * The barrier is shared by all modules, which means that creation of *any*
     * service for any module is single threaded.
     */
    private static final ConcurrentBarrier BARRIER = new ConcurrentBarrier();

    /**
     * "Magic" method related to Serializable that allows the Proxy object to
     * replace itself with the token when being streamed out.
     */
    public ModuleImpl(
        InternalRegistry registry,
        ServiceActivityTracker tracker,
        ModuleDefinition moduleDef,
        JdkProxyFactory proxyFactory,
        Logger logger) {
        this.registry = registry;
        this.tracker = tracker;
        this.proxyFactory = proxyFactory;
        this.moduleDef = moduleDef;
        this.logger = logger;

        for (String id : moduleDef.getServiceIds()) {
            ServiceDefinition sd = moduleDef.getServiceDef(id);

            serviceDefs.put(id, sd);
        }
    }

    @Override
    public <T> T getService(String serviceId, Class<T> serviceInterface) {
        assert InternalUtils.isNonBlank(serviceId);
        assert serviceInterface != null;
        ServiceDefinition def = getServiceDef(serviceId);

        // RegistryImpl should already have checked that the service exists.
        assert def != null;

        Object service = findOrCreate(def);

        try {
            return serviceInterface.cast(service);
        } catch (ClassCastException ex) {
            // This may be overkill: I don't know how this could happen
            // given that the return type of the method determines
            // the service interface.

            throw new RuntimeException(
                IOCMessages.serviceWrongInterface(
                    serviceId,
                    def.getServiceInterface(),
                    serviceInterface));
        }
    }

    @Override
    public Set<AdvisorDefinition> findMatchingServiceAdvisors(ServiceDefinition serviceDef) {
        Set<AdvisorDefinition> result = new HashSet<>();

        for (AdvisorDefinition def : moduleDef.getAdvisorDefs()) {
            if (def.matches(serviceDef) || markerMatched(serviceDef, def))
                result.add(def);
        }

        return result;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Collection<String> findServiceIdsForInterface(
        Class<?> serviceInterface) {
        assert serviceInterface != null;
        Collection<String> result = new ArrayList<>();

        for (ServiceDefinition def : serviceDefs.values()) {
            if (serviceInterface.isAssignableFrom(def.getServiceInterface()))
                result.add(def.getServiceId());
        }

        return result;
    }

    /**
     * Locates the service proxy for a particular service (from the service
     * definition).
     *
     * @param def
     *            defines the service
     * @return the service proxy
     */
    private Object findOrCreate(final ServiceDefinition def) {
        final String key = def.getServiceId();

        final Supplier<Object> create = () -> {
            // In a race condition, two threads may try to create the same service
            // simulatenously.
            // The second will block until after the first creates the service.

            Object result = services.get(key);

            // Normally, result is null, unless some other thread slipped in and created the
            // service
            // proxy.

            if (result == null) {
                result = create(def);

                services.put(key, result);
            }

            return result;
        };

        Supplier<Object> find = () -> {
            Object result = services.get(key);

            if (result == null)
                result = BARRIER.withWrite(create);

            return result;
        };

        return BARRIER.withRead(find);
    }

    @Override
    public void collectEagerLoadServices(
        final Collection<EagerLoadServiceProxy> proxies) {
        Runnable work = new Runnable() {
            @Override
            public void run() {
                for (ServiceDefinition def : serviceDefs.values()) {
                    if (def.isEagerLoad())
                        findOrCreate(def);
                }

                BARRIER.withWrite(() -> {
                    proxies.addAll(eagerLoadProxies);
                    eagerLoadProxies.clear();
                });
            }
        };

        registry.run("Eager loading services", work);
    }

    /**
     * Creates the service and updates the cache of created services.
     */
    private Object create(final ServiceDefinition def) {
        final String serviceId = def.getServiceId();

        final Logger logger = registry.getServiceLogger(serviceId);

        final Class<?> serviceInterface = def.getServiceInterface();

        final boolean canBeProxied = canBeProxiedPredicate.test(
            serviceInterface);
        String description = String.format(
            "Creating %s service %s",
            canBeProxied ? "proxy for" : "non-proxied instance of",
            serviceId);

        if (logger.isDebugEnabled())
            logger.debug(description);

        final Module module = this;

        Supplier<Object> operation = () -> {
            try {
                ServiceBuilderResources resources = new ServiceResourcesImpl(
                    registry,
                    module,
                    def,
                    proxyFactory,
                    logger);

                // Build up a stack of operations that will be needed to realize the service
                // (by the proxy, at a later date).

                ObjectCreator creator = def.createServiceCreator(resources);

                // For non-proxyable services, we immediately create the service implementation
                // and return it. There's no interface to proxy, which throws out the
                // possibility of
                // deferred instantiation, service lifecycles, and decorators.

                ServiceLifecycle lifecycle = registry.getServiceLifecycle(
                    def.getServiceScope());

                if (!canBeProxied) {
                    if (lifecycle.requiresProxy())
                        throw new IllegalArgumentException(
                            String.format(
                                "Service scope '%s' requires a proxy, but the service does not have a service interface (necessary to create a proxy). Provide a service interface or select a different service scope.",
                                def.getServiceScope()));

                    return creator.create();
                }

                creator = new OperationTrackingObjectCreator(
                    registry,
                    String.format(
                        "Instantiating service %s implementation via %s",
                        serviceId,
                        creator),
                    creator);

                creator = new LifecycleWrappedServiceCreator(
                    lifecycle,
                    resources,
                    creator);

                // Marked services (or services inside marked modules) are not decorated.
                // FreewayIOCModule prevents decoration of its services. Note that all
                // decorators will decorate
                // around the aspect interceptor, which wraps around the core service
                // implementation.

                boolean allowDecoration = !def.isPreventDecoration();

                if (allowDecoration) {
                    creator = new AdvisorStackBuilder(
                        def,
                        creator,
                        getAspectDecorator(),
                        registry);
                }

                // Add a wrapper that checks for recursion.

                creator = new RecursiveServiceCreationCheckWrapper(
                    def,
                    creator,
                    logger);

                creator = new OperationTrackingObjectCreator(
                    registry,
                    "Realizing service " + serviceId,
                    creator);

                JustInTimeObjectCreator delegate = new JustInTimeObjectCreator(
                    tracker,
                    creator,
                    serviceId);

                Object proxy = createProxy(
                    resources,
                    delegate,
                    def.isPreventDecoration());

                registry.addRegistryShutdownListener(delegate);

                if (def.isEagerLoad())
                    eagerLoadProxies.add(delegate);

                tracker.setStatus(serviceId, ServiceStatus.VIRTUAL);

                return proxy;
            } catch (Exception ex) {
                logger.error("Failed to create service '{}': {}", serviceId, ex.getMessage(), ex);
                throw new RuntimeException(
                    IOCMessages.errorBuildingService(serviceId, def, ex),
                    ex);
            }
        };

        return registry.invoke(description, operation);
    }

    private AspectInterceptor getAspectDecorator() {
        return registry.invoke("Obtaining AspectDecorator service", () -> registry.getService(AspectInterceptor.class));
    }

    private final Runnable instantiateModule = new Runnable() {
        @Override
        public void run() {
            moduleInstance = registry.invoke(
                "Constructing module class " +
                    moduleDef.getBuilderClass().getName(),
                () -> instantiateModuleInstance());
        }
    };

    private final Supplier<Object> provideModuleInstance = () -> {
        if (moduleInstance == null)
            BARRIER.withWrite(instantiateModule);

        return moduleInstance;
    };

    @Override
    public Object getModuleBuilder() {
        return BARRIER.withRead(provideModuleInstance);
    }

    private Object instantiateModuleInstance() {
        Class<?> moduleClass = moduleDef.getBuilderClass();

        Constructor[] constructors = moduleClass.getConstructors();

        if (constructors.length == 0)
            throw new RuntimeException(
                IOCMessages.noPublicConstructors(moduleClass));

        if (constructors.length > 1) {
            // Sort the constructors ascending by number of parameters (descending); this is
            // really
            // just to allow the test suite to work properly across different JVMs (which
            // will
            // often order the constructors differently).

            Comparator<Constructor> comparator = new Comparator<Constructor>() {
                @Override
                public int compare(Constructor c1, Constructor c2) {
                    return (c2.getParameterTypes().length -
                        c1.getParameterTypes().length);
                }
            };

            Arrays.sort(constructors, comparator);

            logger.warn(
                IOCMessages.tooManyPublicConstructors(
                    moduleClass,
                    constructors[0]));
        }

        Constructor constructor = constructors[0];

        if (insideConstructor)
            throw new RuntimeException(
                IOCMessages.recursiveModuleConstructor(moduleClass, constructor));

        ServiceLocator locator = new ServiceLocatorImpl(registry, this);
        Map<Class<?>, Object> resourcesMap = new HashMap<>();

        resourcesMap.put(Logger.class, logger);
        resourcesMap.put(ServiceLocator.class, locator);
        resourcesMap.put(OperationTracker.class, registry);

        InjectionResources resources = new MapInjectionResources(resourcesMap);

        Throwable fail = null;

        try {
            insideConstructor = true;

            ObjectCreator[] parameterValues = InternalUtils.calculateParameters(
                locator,
                resources,
                constructor.getParameterTypes(),
                constructor.getGenericParameterTypes(),
                constructor.getParameterAnnotations(),
                registry);

            Object[] realized = InternalUtils.realizeObjects(parameterValues);

            java.lang.invoke.MethodHandle ctorHandle = com.jujin.freeway.ioc.internal.util.MethodHandleUtils
                .constructorHandle(
                    constructor);
            Object result = ctorHandle.invokeWithArguments(realized);

            InternalUtils.injectIntoFields(
                result,
                locator,
                resources,
                registry);

            return result;
        } catch (Throwable ex) {
            fail = (ex instanceof java.lang.reflect.InvocationTargetException)
                ? ((java.lang.reflect.InvocationTargetException) ex).getTargetException()
                : ex;
        } finally {
            insideConstructor = false;
        }

        throw new RuntimeException(
            IOCMessages.instantiateBuilderError(moduleClass, fail),
            fail);
    }

    private Object createProxy(
        ServiceResources resources,
        ObjectCreator creator,
        boolean preventDecoration) {
        String serviceId = resources.getServiceId();
        Class<?> serviceInterface = resources.getServiceInterface();

        String toString = format(
            "<Proxy for %s(%s)>",
            serviceId,
            serviceInterface.getName());

        ServiceProxyToken token = SerializationSupport.createToken(serviceId);

        return createProxyInstance(
            creator,
            token,
            serviceInterface,
            serviceId,
            toString);
    }

    private Object createProxyInstance(
        final ObjectCreator creator,
        final ServiceProxyToken token,
        final Class<?> serviceInterface,
        final String serviceId,
        final String description) {
        InvocationHandler handler = new LazyProxyHandler(
            creator,
            token,
            serviceInterface,
            serviceId,
            description);

        return Proxy.newProxyInstance(
            serviceInterface.getClassLoader(),
            new Class[]{ serviceInterface, Serializable.class },
            handler);
    }

    /**
     * JDK dynamic proxy handler that lazily invokes the ObjectCreator. On first
     * method call, composes pre-bound MethodHandles for all interface methods.
     * Subsequent calls use {@code handle.invoke(args)} directly — no array
     * allocation, no {@code invokeWithArguments} overhead, no per-call MethodHandle
     * lookup.
     *
     * <p>
     * Supports serialization via ServiceProxyToken.
     * </p>
     */
    private static class LazyProxyHandler
        implements InvocationHandler, Serializable {

        private static final long serialVersionUID = 1L;

        private final String serviceId;
        private final Class<?> serviceInterface;
        private final String description;
        private transient ObjectCreator creator;

        /** Pre-composed MethodHandles, set after first service creation. */
        private transient MethodHandleInfo[] dispatchTable;

        /** The cached service instance (lazy). */
        private transient Object service;

        LazyProxyHandler(
            ObjectCreator creator,
            ServiceProxyToken token,
            Class<?> serviceInterface,
            String serviceId,
            String description) {
            this.creator = creator;
            this.serviceId = serviceId;
            this.serviceInterface = serviceInterface;
            this.description = description;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args)
            throws Throwable {
            // Fast path: Object methods (use declaring class check, not String.equals)
            if (method.getDeclaringClass() == Object.class) {
                return switch (method.getName()) {
                    case "toString" -> description;
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> method.invoke(this, args);
                };
            }

            // Ensure dispatch table is built (first-call synchronization)
            MethodHandleInfo[] table = dispatchTable;
            if (table == null) {
                table = buildDispatchTable();
            }

            // O(1) dispatch: static field stores the method's index
            int index = table[0].getMethodIndex(method);
            if (index >= 0) {
                return table[index].invoke(args);
            }

            throw new AbstractMethodError("Unknown method: " + method);
        }

        /**
         * Builds the dispatch table on first method call. Slot 0 is the index lookup
         * helper; slots 1..N are per-method handles.
         */
        private synchronized MethodHandleInfo[] buildDispatchTable() {
            if (dispatchTable != null)
                return dispatchTable;

            Object svc = (service != null) ? service : createService();
            this.service = svc; // cache for serialization fallback

            java.lang.reflect.Method[] methods = serviceInterface.getMethods();
            MethodHandleInfo[] table = new MethodHandleInfo[methods.length + 1]; // 0 = indexer
            table[0] = new MethodHandleInfo(methods, null);

            for (int i = 1; i <= methods.length; i++) {
                var m = methods[i - 1];
                if (m.getDeclaringClass() == Object.class) {
                    table[i] = null;
                    continue;
                }
                table[i] = new MethodHandleInfo(methods, composeHandle(m, svc));
            }

            this.dispatchTable = table;
            return table;
        }

        /**
         * Composes a MethodHandle for a single method with the service pre-bound.
         * Result signature: {@code (Object[])Object}, accepts InvocationHandler args
         * directly.
         */
        private static java.lang.invoke.MethodHandle composeHandle(
            java.lang.reflect.Method method,
            Object service) {
            try {
                java.lang.invoke.MethodHandle raw = com.jujin.freeway.ioc.internal.util.MethodHandleUtils.methodHandle(
                    method).bindTo(service);
                int paramCount = method.getParameterCount();
                if (paramCount == 0) {
                    // No-arg method: handle is already ()Object
                    // Wrap to accept (Object[])Object and ignore args
                    return java.lang.invoke.MethodHandles.dropArguments(
                        raw,
                        0,
                        Object[].class);
                }
                // Spread the Object[] from InvocationHandler across parameters
                return raw
                    .asSpreader(Object[].class, paramCount)
                    .asType(
                        java.lang.invoke.MethodType.methodType(
                            Object.class,
                            Object[].class));
            } catch (Exception e) {
                throw new RuntimeException(
                    "Failed to compose handle for " + method,
                    e);
            }
        }

        private Object createService() {
            if (creator != null) {
                return creator.create();
            }
            return SerializationSupport.readResolve(serviceId);
        }

        /**
         * Lightweight flyweight: holds either the method-index lookup table (slot 0) or
         * a single composed MethodHandle (slots 1..N).
         */
        private static final class MethodHandleInfo {

            private final java.util.Map<java.lang.reflect.Method, Integer> methodIndex; // slot 0 only
            private final java.lang.invoke.MethodHandle handle; // slots 1..N

            MethodHandleInfo(
                java.lang.reflect.Method[] methods,
                java.lang.invoke.MethodHandle handle) {
                this.methodIndex = (methods != null)
                    ? buildIndex(methods)
                    : null;
                this.handle = handle;
            }

            private static java.util.Map<java.lang.reflect.Method, Integer> buildIndex(
                java.lang.reflect.Method[] methods) {
                var map = new java.util.HashMap<java.lang.reflect.Method, Integer>(methods.length * 2);
                for (int i = 0; i < methods.length; i++) {
                    map.put(methods[i], i + 1); // 1-based
                }
                return map;
            }

            /** Find the 1-based index of the given method. Returns -1 if not found. */
            int getMethodIndex(java.lang.reflect.Method method) {
                Integer idx = methodIndex.get(method);
                return idx != null ? idx : -1;
            }

            Object invoke(Object[] args) throws Throwable {
                return handle.invoke(args);
            }
        }
    }

    @Override
    public Set<ContributionDef> getContributorDefsForService(
        ServiceDefinition serviceDef) {
        Set<ContributionDef> result = new HashSet<>();

        for (ContributionDef next : moduleDef.getContributionDefs()) {
            if (serviceDef.getServiceId().equalsIgnoreCase(next.getServiceId())) {
                result.add(next);
            } else {
                if (markerMatched(serviceDef, next)) {
                    result.add(next);
                }
            }
        }

        return result;
    }

    private boolean markerMatched(ServiceDefinition serviceDef, Markable markable) {
        final Class<?> markableInterface = markable.getServiceInterface();

        if (markableInterface == null ||
            !markableInterface.isAssignableFrom(
                serviceDef.getServiceInterface()))
            return false;

        Set<Class<?>> contributionMarkers = new HashSet<>(
            markable.getMarkers());

        if (contributionMarkers.contains(Local.class)) {
            // If @Local is present, filter out services that aren't in the same module.
            // Don't consider @Local to be a marker annotation
            // for the later match, however.

            if (!isLocalServiceDef(serviceDef))
                return false;

            contributionMarkers.remove(Local.class);
        }

        // Filter out any stray annotations that aren't used by some
        // service, in any module, as a marker annotation.

        contributionMarkers.retainAll(registry.getMarkerAnnotations());

        // @Advise and @Decorate default to Object.class service interface.
        // If @Match is present, no marker annotations are needed.
        // In such a case an empty contribution marker list should be ignored.
        if (markableInterface == Object.class && contributionMarkers.isEmpty())
            return false;

        return serviceDef.getMarkers().containsAll(contributionMarkers);
    }

    private boolean isLocalServiceDef(ServiceDefinition serviceDef) {
        return serviceDefs.containsKey(serviceDef.getServiceId());
    }

    @Override
    public ServiceDefinition getServiceDef(String serviceId) {
        return serviceDefs.get(serviceId);
    }

    @Override
    public String getLoggerName() {
        return moduleDef.getLoggerName();
    }

    @Override
    public String toString() {
        return String.format("ModuleImpl[%s]", moduleDef.getLoggerName());
    }
}
