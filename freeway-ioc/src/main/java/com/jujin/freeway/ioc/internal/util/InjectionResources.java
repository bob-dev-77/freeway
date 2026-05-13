package com.jujin.freeway.ioc.internal.util;

import java.lang.reflect.Type;

/**
 * Provides for the injection of specific types of values as <em>resources</em>
 * as opposed to services or objects obtained from
 * {@link com.jujin.freeway.ioc.ObjectInjector}. This includes values such as a
 * service's logger, service interface class, or
 * {@link com.jujin.freeway.ioc.ServiceResources}.
 */
public interface InjectionResources {
    /**
     * Given the field type, provide the matching resource value, or null.
     *
     * @param type
     *            type of field or parameter
     * @param genericType
     *            generic type information associated with field or parameter
     * @return the corresponding value, or null
     */
    <T> T findResource(Class<T> type, Type genericType);
}
