package com.jujin.freeway.ioc.internal;

import com.jujin.freeway.ioc.ServiceBuilderContext;
import com.jujin.freeway.ioc.ServiceLocator;
import com.jujin.freeway.ioc.ServiceContext;
import com.jujin.freeway.ioc.advisor.OperationTracker;
import com.jujin.freeway.ioc.internal.util.DelegatingInjectionContext;
import com.jujin.freeway.ioc.internal.util.InjectionContext;
import com.jujin.freeway.ioc.internal.util.MappedInjectionContext;
import org.slf4j.Logger;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.jujin.freeway.ioc.internal.ConfigurationType.*;

/**
 * Builds {@link InjectionContext} for service creators, combining core
 * injection resources (ServiceLocator, ServiceContext, Logger, Class,
 * OperationTracker) with configuration-based resources (unordered, ordered,
 * mapped).
 */
public class InjectionContextBuilder {

    final String serviceId;

    private final Map<Class<?>, Object> injectionResources = new HashMap<>();

    final ServiceBuilderContext resources;

    final Logger logger;

    final String creatorDescription;

    private static final Map<Class<?>, ConfigurationType> PARAMETER_TYPE_TO_CONFIGURATION_TYPE = new HashMap<>();

    static {
        PARAMETER_TYPE_TO_CONFIGURATION_TYPE.put(Collection.class, UNORDERED);
        PARAMETER_TYPE_TO_CONFIGURATION_TYPE.put(List.class, ORDERED);
        PARAMETER_TYPE_TO_CONFIGURATION_TYPE.put(Map.class, MAPPED);
    }

    public InjectionContextBuilder(
        ServiceBuilderContext resources,
        String creatorDescription) {
        serviceId = resources.getServiceId();

        this.resources = resources;
        this.creatorDescription = creatorDescription;
        logger = resources.getLogger();

        injectionResources.put(ServiceLocator.class, resources);
        injectionResources.put(ServiceContext.class, resources);
        injectionResources.put(Logger.class, logger);
        injectionResources.put(Class.class, resources.getServiceInterface());
        injectionResources.put(OperationTracker.class, resources.getTracker());
    }

    /**
     * Returns a map (based on injectionResources) that includes (possibly) an
     * additional mapping containing the collected configuration data. This involves
     * scanning the parameters and generic types.
     */
    public InjectionContext build() {
        InjectionContext core = new MappedInjectionContext(injectionResources);

        InjectionContext configurations = new InjectionContext() {
            private boolean seenOne;

            @Override
            public <T> T findResource(Class<T> resourceType, Type genericType) {
                ConfigurationType thisType = PARAMETER_TYPE_TO_CONFIGURATION_TYPE.get(resourceType);

                if (thisType == null)
                    return null;

                if (seenOne)
                    throw new RuntimeException(
                        String.format(
                            "Service builder method %s contains more than one parameter of type Collection, List, or Map. Parameters of this type are the way in which service configuration values, collected from service contributor methods, are provided to the service builder. Services are only allowed a single configuration",
                            creatorDescription));

                seenOne = true;

                switch (thisType) {
                    case UNORDERED:
                        return resourceType.cast(
                            getUnorderedConfiguration(genericType));
                    case ORDERED:
                        return resourceType.cast(
                            getOrderedConfiguration(genericType));
                    case MAPPED:
                        return resourceType.cast(
                            getMappedConfiguration(genericType));
                }

                return null;
            }
        };

        return new DelegatingInjectionContext(core, configurations);
    }

    private List<?> getOrderedConfiguration(Type genericType) {
        Class<?> valueType = findParameterizedTypeFromGenericType(genericType);

        return resources.getOrderedConfiguration(valueType);
    }

    private Collection<?> getUnorderedConfiguration(Type genericType) {
        Class<?> valueType = findParameterizedTypeFromGenericType(genericType);

        return resources.getUnorderedConfiguration(valueType);
    }

    private Map<?, ?> getMappedConfiguration(Type genericType) {
        Class<?> keyType = findParameterizedTypeFromGenericType(genericType, 0);
        Class<?> valueType = findParameterizedTypeFromGenericType(
            genericType,
            1);

        if (keyType == null || valueType == null)
            throw new IllegalArgumentException(
                String.format(
                    "Generic type '%s' is not supported. Only simple parameterized lists are supported.",
                    genericType));

        return resources.getMappedConfiguration(keyType, valueType);
    }

    /**
     * Extracts from a generic type the underlying parameterized type. I.e., for
     * List<Runnable>, will return Runnable. This is limited to simple parameterized
     * types, not the more complex cases involving wildcards and upper/lower
     * boundaries.
     *
     * @param type
     *            the genetic type of the parameter, i.e., List<Runnable>
     * @return the parameterize type (i.e. Runnable.class if type represents
     *         List<Runnable>).
     */

    // package private for testing
    static Class<?> findParameterizedTypeFromGenericType(Type type) {
        Class<?> result = findParameterizedTypeFromGenericType(type, 0);

        if (result == null)
            throw new IllegalArgumentException(
                String.format(
                    "Generic type '%s' is not supported. Only simple parameterized lists are supported.",
                    type));

        return result;
    }

    /**
     * "Sniffs" a generic type to find the underlying parameterized type. If the
     * Type is a class, then Object.class is returned. Otherwise, the type must be a
     * ParameterizedType. We check to make sure it has the correct number of a
     * actual types (1 for a Collection or List, 2 for a Map). The actual types must
     * be classes (wildcards just aren't supported)
     *
     * @param type
     *            a Class or ParameterizedType to inspect
     * @param typeIndex
     *            the index within the ParameterizedType to extract
     * @return the actual type, or Object.class if the input type is not generic, or
     *         null if any other pre-condition is not met
     */
    private static Class<?> findParameterizedTypeFromGenericType(
        Type type,
        int typeIndex) {
        // For a raw Class type, it means the parameter is not parameterized (i.e.
        // Collection, not
        // Collection<Foo>), so we can return Object.class to allow no restriction.

        if (type instanceof Class<?>)
            return Object.class;

        if (!(type instanceof ParameterizedType))
            return null;

        ParameterizedType pt = (ParameterizedType) type;

        Type[] types = pt.getActualTypeArguments();

        Type actualType = types[typeIndex];

        return actualType instanceof Class<?> c ? c
            : actualType instanceof ParameterizedType apt ? (Class<?>) apt.getRawType() : null;
    }
}
