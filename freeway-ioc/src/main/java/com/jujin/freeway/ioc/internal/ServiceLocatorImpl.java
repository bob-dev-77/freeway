package com.jujin.freeway.ioc.internal;

import com.jujin.freeway.ioc.AnnotationProvider;
import com.jujin.freeway.ioc.ServiceLocator;
import java.lang.annotation.Annotation;
import java.util.Collection;

/**
 * Base service locator class used when only the module is known (i.e., when
 * instantiating a module class).
 */
public class ServiceLocatorImpl implements ServiceLocator {

    private final InternalRegistry registry;

    private final Module module;

    public ServiceLocatorImpl(InternalRegistry registry, Module module) {
        this.registry = registry;
        this.module = module;
    }

    @Override
    public <T> T getService(String serviceId, Class<T> serviceInterface) {
        String expandedServiceId = registry.expandSymbols(serviceId);

        return registry.getService(expandedServiceId, serviceInterface);
    }

    @Override
    public <T> T getService(Class<T> serviceInterface) {
        return registry.getService(serviceInterface);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getService(
        Class<T> serviceInterface,
        Class<? extends Annotation>... markerTypes) {
        return registry.getService(serviceInterface, markerTypes);
    }

    @Override
    public <T> Collection<T> getServices(Class<T> serviceInterface) {
        return registry.getServices(serviceInterface);
    }

    @Override
    public <T> T getObject(
        Class<T> objectType,
        AnnotationProvider annotationProvider) {
        return registry.getObject(objectType, annotationProvider, this, module);
    }

    @Override
    public <T> T autobuild(Class<T> clazz) {
        return registry.autobuild(clazz);
    }

    @Override
    public <T> T autobuild(String description, Class<T> clazz) {
        return registry.autobuild(description, clazz);
    }

    @Override
    public <T> T proxy(
        Class<T> interfaceClass,
        Class<? extends T> implementationClass) {
        return registry.proxy(interfaceClass, implementationClass, this);
    }
}
