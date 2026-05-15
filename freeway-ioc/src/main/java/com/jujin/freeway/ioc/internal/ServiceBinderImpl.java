package com.jujin.freeway.ioc.internal;

import com.jujin.freeway.ioc.*;
import com.jujin.freeway.ioc.annotations.*;
import com.jujin.freeway.ioc.internal.util.InternalUtils;
import com.jujin.freeway.ioc.internal.util.OneShotLock;
import com.jujin.freeway.ioc.lifecycle.ObjectCreator;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class ServiceBinderImpl implements ServiceBinder, ServiceBindingOptions {

    private final OneShotLock lock = new OneShotLock();

    private final Method bindMethod;

    private final ServiceDefAccumulator accumulator;

    private JdkProxyFactory proxyFactory;

    private final Set<Class<?>> defaultMarkers;

    private final boolean moduleDefaultPreventDecoration;

    public ServiceBinderImpl(
        ServiceDefAccumulator accumulator,
        Method bindMethod,
        JdkProxyFactory proxyFactory,
        Set<Class<?>> defaultMarkers,
        boolean moduleDefaultPreventDecoration) {
        this.accumulator = accumulator;
        this.bindMethod = bindMethod;
        this.proxyFactory = proxyFactory;
        this.defaultMarkers = defaultMarkers;
        this.moduleDefaultPreventDecoration = moduleDefaultPreventDecoration;

        clear();
    }

    private String serviceId;

    private Class<?> serviceInterface;

    private Class<?> serviceImplementation;

    private final Set<Class<?>> markers = new HashSet<>();

    private ObjectCreatorStrategy source;

    private boolean eagerLoad;

    private String scope;

    private boolean preventDecoration;

    private boolean preventReloading;

    public void finish() {
        lock.lock();

        flush();
    }

    protected void flush() {
        if (serviceInterface == null)
            return;

        // source will be null when the implementation class is provided; non-null when
        // using
        // a ServiceBuilder callback

        if (source == null)
            source = createObjectCreatorStrategyFromImplementationClass();

        // Combine service-specific markers with those inherited form the module.
        Set<Class<?>> markers = new HashSet<>(defaultMarkers);
        markers.addAll(this.markers);

        ServiceDefinition serviceDef = new ServiceDefinitionImpl(
            serviceInterface,
            serviceImplementation,
            serviceId,
            markers,
            scope,
            eagerLoad,
            preventDecoration,
            source);

        accumulator.addServiceDef(serviceDef);

        clear();
    }

    private void clear() {
        serviceId = null;
        serviceInterface = null;
        serviceImplementation = null;
        source = null;
        this.markers.clear();
        eagerLoad = false;
        scope = null;
        preventDecoration = moduleDefaultPreventDecoration;
        preventReloading = false;
    }

    private ObjectCreatorStrategy createObjectCreatorStrategyFromImplementationClass() {
        if (InternalUtils.SERVICE_CLASS_RELOADING_ENABLED &&
            !preventReloading &&
            isProxiable() &&
            reloadableScope() &&
            InternalUtils.isLocalFile(serviceImplementation))
            return createReloadableConstructorBasedObjectCreatorStrategy();

        return createStandardConstructorBasedObjectCreatorStrategy();
    }

    private boolean isProxiable() {
        return serviceInterface.isInterface();
    }

    private boolean reloadableScope() {
        return scope.equalsIgnoreCase(InternalUtils.DEFAULT);
    }

    private ObjectCreatorStrategy createStandardConstructorBasedObjectCreatorStrategy() {
        if (Modifier.isAbstract(serviceImplementation.getModifiers()))
            throw new RuntimeException(
                IOCMessages.abstractServiceImplementation(
                    serviceImplementation,
                    serviceId));
        final Constructor constructor = InternalUtils.findAutobuildConstructor(
            serviceImplementation);

        if (constructor == null)
            throw new RuntimeException(
                IOCMessages.noConstructor(serviceImplementation, serviceId));

        return new ObjectCreatorStrategy() {
            @Override
            public ObjectCreator constructCreator(
                ServiceBuilderResources resources) {
                return new ConstructorServiceCreator(
                    resources,
                    getDescription(),
                    constructor);
            }

            @Override
            public String getDescription() {
                return String.format(
                    "%s via %s",
                    constructor.getName(),
                    InternalUtils.asString(bindMethod));
            }
        };
    }

    private ObjectCreatorStrategy createReloadableConstructorBasedObjectCreatorStrategy() {
        return new ReloadableObjectCreatorStrategy(
            proxyFactory,
            bindMethod,
            serviceInterface,
            serviceImplementation,
            eagerLoad);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> ServiceBindingOptions bind(Class<T> serviceClass) {
        if (serviceClass.isInterface()) {
            try {
                String expectedImplName = serviceClass.getName() + "Impl";

                ClassLoader classLoader = proxyFactory.getClassLoader();

                Class<T> implementationClass = (Class<T>) classLoader.loadClass(
                    expectedImplName);

                if (!implementationClass.isInterface() &&
                    serviceClass.isAssignableFrom(implementationClass)) {
                    return bind(serviceClass, implementationClass);
                }
                throw new RuntimeException(
                    IOCMessages.noServiceMatchesType(serviceClass));
            } catch (ClassNotFoundException ex) {
                throw new RuntimeException(
                    String.format(
                        "Could not find default implementation class %sImpl. Please provide this class, or bind the service interface to a specific implementation class.",
                        serviceClass.getName()));
            }
        }

        return bind(serviceClass, serviceClass);
    }

    @Override
    public <T> ServiceBindingOptions bind(
        Class<T> serviceInterface,
        final ServiceBuilder<T> builder) {
        if (serviceInterface == null) {
            throw new IllegalArgumentException("serviceInterface must not be null");
        }
        if (builder == null) {
            throw new IllegalArgumentException("builder must not be null");
        }
        lock.check();

        flush();

        this.serviceInterface = serviceInterface;
        this.scope = InternalUtils.DEFAULT;

        serviceId = serviceInterface.getSimpleName();

        this.source = new ObjectCreatorStrategy() {
            @Override
            public ObjectCreator constructCreator(
                final ServiceBuilderResources resources) {
                return () -> builder.buildService(resources);
            }

            @Override
            public String getDescription() {
                return InternalUtils.asString(bindMethod);
            }
        };

        return this;
    }

    @Override
    public <T> ServiceBindingOptions bind(
        Class<T> serviceInterface,
        Class<? extends T> serviceImplementation) {
        if (serviceInterface == null) {
            throw new IllegalArgumentException("serviceInterface must not be null");
        }
        if (serviceImplementation == null) {
            throw new IllegalArgumentException("serviceImplementation must not be null");
        }
        lock.check();

        flush();

        this.serviceInterface = serviceInterface;

        this.serviceImplementation = serviceImplementation;

        // Set defaults for the other properties.

        eagerLoad = serviceImplementation.getAnnotation(EagerLoad.class) != null;

        serviceId = InternalUtils.getServiceId(serviceImplementation);

        if (serviceId == null) {
            serviceId = serviceInterface.getSimpleName();
        }

        Scope scope = serviceImplementation.getAnnotation(Scope.class);

        this.scope = scope != null ? scope.value() : InternalUtils.DEFAULT;

        Marker marker = serviceImplementation.getAnnotation(Marker.class);

        if (marker != null) {
            InternalUtils.validateMarkerAnnotations(marker.value());
            @SuppressWarnings("unchecked")
            Class<? extends Annotation>[] markerValues = (Class<? extends Annotation>[]) (Object[]) marker.value();
            for (Class<? extends Annotation> m : markerValues) {
                markers.add(m);
            }
        }

        // Treat @Primary directly on the implementation class as a marker
        if (serviceImplementation.getAnnotation(Primary.class) != null) {
            markers.add(Primary.class);
        }

        preventDecoration |= serviceImplementation.getAnnotation(
            PreventServiceDecoration.class) != null;

        return this;
    }

    @Override
    public ServiceBindingOptions eagerLoad() {
        lock.check();

        eagerLoad = true;

        return this;
    }

    @Override
    public ServiceBindingOptions preventDecoration() {
        lock.check();

        preventDecoration = true;

        return this;
    }

    @Override
    public ServiceBindingOptions preventReloading() {
        lock.check();

        preventReloading = true;

        return this;
    }

    @Override
    public ServiceBindingOptions withId(String id) {
        if (!InternalUtils.isNonBlank(id)) {
            throw new IllegalArgumentException("id must not be null or blank");
        }
        lock.check();

        serviceId = id;

        return this;
    }

    @Override
    public ServiceBindingOptions withSimpleId() {
        if (serviceImplementation == null) {
            throw new IllegalArgumentException(
                "No defined implementation class to generate simple id from.");
        }

        return withId(serviceImplementation.getSimpleName());
    }

    @Override
    public ServiceBindingOptions scope(String scope) {
        if (!InternalUtils.isNonBlank(scope)) {
            throw new IllegalArgumentException("scope must not be null or blank");
        }
        lock.check();

        this.scope = scope;

        return this;
    }

    @Override
    public ServiceBindingOptions withMarker(
        Class<? extends Annotation>... marker) {
        lock.check();

        InternalUtils.validateMarkerAnnotations(marker);

        markers.addAll(Arrays.asList(marker));

        return this;
    }
}
