package com.jujin.freeway.ioc;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Extends {@link ServiceContext} with additional
 * methods needed only by the service builder method, related to accessing a
 * service's configuration. Services may have a <em>single</em> configuration in
 * one of three flavors: unordered, ordered or mapped.
 */
public interface ServiceBuilderContext extends ServiceContext, ModuleInstanceSource {

    <T> Collection<T> getUnorderedConfiguration(Class<T> valueType);

    <T> List<T> getOrderedConfiguration(Class<T> valueType);

    <K, V> Map<K, V> getMappedConfiguration(Class<K> keyType, Class<V> valueType);
}
