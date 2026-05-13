package com.jujin.freeway.ioc.internal;

import com.jujin.freeway.ioc.AnnotationProvider;
import com.jujin.freeway.ioc.ServiceBuilderResources;
import com.jujin.freeway.ioc.ServiceDefinition;
import com.jujin.freeway.ioc.internal.util.InternalUtils;
import com.jujin.freeway.ioc.lifecycle.ObjectCreator;

import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ServiceDefinitionImpl implements ServiceDefinition {

    private final Class<?> serviceInterface;

    private final Class<?> serviceImplementation;

    private final String serviceId;

    private final String scope;

    private final boolean eagerLoad;

    private final ObjectCreatorStrategy source;

    private final Set<Class<?>> markers;

    private final boolean preventDecoration;

    /**
     * @param serviceInterface
     *            interface implemented by the service (or the service
     *            implementation class, for non-proxied services)
     * @param serviceImplementation
     *            service implementation class if exists
     * @param serviceId
     *            unique id for the service
     * @param markers
     *            set of marker annotation classes (will be retained not copied)
     * @param scope
     *            scope of the service (i.e.,
     *            {@link com.jujin.freeway.ioc.internal.util.InternalUtils#DEFAULT}).
     * @param eagerLoad
     *            if true, the service is realized at startup, rather than on-demand
     * @param preventDecoration
     *            if true, the service may not be decorated
     * @param source
     *            used to create the service implementation when needed
     */
    ServiceDefinitionImpl(
        Class<?> serviceInterface,
        Class<?> serviceImplementation,
        String serviceId,
        Set<Class<?>> markers,
        String scope,
        boolean eagerLoad,
        boolean preventDecoration,
        ObjectCreatorStrategy source) {
        this.serviceInterface = serviceInterface;
        this.serviceImplementation = serviceImplementation;
        this.serviceId = serviceId;
        this.scope = scope;
        this.eagerLoad = eagerLoad;
        this.preventDecoration = preventDecoration;
        this.source = source;

        this.markers = markers;
    }

    @Override
    public String toString() {
        return source.getDescription();
    }

    @Override
    public ObjectCreator<?> createServiceCreator(
        ServiceBuilderResources resources) {
        return source.constructCreator(resources);
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
    public Class<?> getServiceImplementation() {
        return serviceImplementation;
    }

    @Override
    public String getServiceScope() {
        return scope;
    }

    @Override
    public boolean isEagerLoad() {
        return eagerLoad;
    }

    @Override
    public Set<Class<?>> getMarkers() {
        return markers;
    }

    @Override
    public boolean isPreventDecoration() {
        return preventDecoration;
    }

    private Stream<Class<?>> searchPath() {
        return Stream.of(serviceImplementation, serviceInterface).filter(
            Objects::nonNull);
    }

    @Override
    public AnnotationProvider getClassAnnotationProvider() {
        return AnnotationProviderChain.create(
            searchPath()
                .map(InternalUtils.CLASS_TO_AP_MAPPER)
                .collect(Collectors.toList()));
    }

    @Override
    @SuppressWarnings("rawtypes")
    public AnnotationProvider getMethodAnnotationProvider(
        final String methodName,
        final Class... argumentTypes) {
        return AnnotationProviderChain.create(
            searchPath()
                .map(element -> InternalUtils.findMethod(element, methodName, argumentTypes))
                .map(InternalUtils.METHOD_TO_AP_MAPPER)
                .collect(Collectors.toList()));
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((serviceId == null) ? 0 : serviceId.hashCode());
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
        if (!(obj instanceof ServiceDefinition)) {
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
}
