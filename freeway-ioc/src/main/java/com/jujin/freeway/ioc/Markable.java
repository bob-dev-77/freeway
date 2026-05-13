package com.jujin.freeway.ioc;

import java.util.Set;

/**
 * Interface implemented by objects which need to disambiguate services with
 * marker annotations.
 *
 */
public interface Markable {
    /**
     * Returns an optional set of <em>marker annotation</em>. Marker annotations are
     * used to disambiguate services; the combination of a marker annotation and a
     * service type is expected to be unique. Note that it is not possible to
     * identify which annotations are markers and which are not when this set is
     * constructed, so it may include non-marker annotations.
     *
     * @see ServiceDefinition#getMarkers()
     */
    Set<Class<?>> getMarkers();

    /**
     * Returns the service interface associated with the service.
     *
     * @see ServiceDefinition#getServiceInterface()
     */
    Class<?> getServiceInterface();
}
