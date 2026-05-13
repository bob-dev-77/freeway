package com.jujin.freeway.ioc;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * An object which manages a list of
 * {@link com.jujin.freeway.ioc.InvalidationListener}s. There are multiple event
 * hub services implementing this interface, each with a specific marker
 * annotation; each can register listeners and fire events; these are based on
 * the type of resource that has been invalidated. Freeway has built-in support
 * for:
 * <dl>
 * <dt>message catalog resources
 * <dd>ComponentMessages marker annotation
 * <dt>component templates
 * <dd>ComponentTemplates marker annotation
 * <dt>component classes
 * <dd>{@link com.jujin.freeway.ioc.annotations.ComponentClasses} marker
 * annotation
 * </dl>
 * <p>
 * Starting in Freeway 5.3, these services are disabled in production (it does
 * nothing).
 *
 */
public interface InvalidationEventHub {
    /**
     * Adds a callback that is invoked when an underlying tracked resource has
     * changed. Does nothing in production mode.
     *
     */
    void addInvalidationCallback(Runnable callback);

    /**
     * Adds a callback that clears the map.
     *
     */
    void clearOnInvalidation(Map<?, ?> map);

    /**
     * Adds a callback, as a function that receives a list of strings and also
     * returns a list of strings, that is invoked when one or more listed underlying
     * tracked resource have changed. An empty list should be considered as all
     * resources being changed and any caches needing to be cleared. The return
     * value of the function should be a non-null, but possibly empty, list of other
     * resources that also need to be invalidated in a recursive fashion. This
     * method does nothing in production mode.
     *
     */
    void addInvalidationCallback(Function<List<String>, List<String>> function);

    /**
     * Notify resource-specific invalidations to listeners.
     *
     */
    void fireInvalidationEvent(List<String> resources);
}
