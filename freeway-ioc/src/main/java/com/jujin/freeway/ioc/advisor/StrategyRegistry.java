package com.jujin.freeway.ioc.advisor;

import com.jujin.freeway.ioc.exception.UnknownValueException;

import com.jujin.freeway.ioc.internal.InheritanceSearch;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A key component in implementing the "Gang of Four" Strategy pattern. A
 * StrategyRegistry will match up a given input type with a registered strategy
 * for that type.
 *
 * @param <A>
 *            the type of the strategy adapter
 */
public final class StrategyRegistry<A> {

    private final Class<A> adapterType;

    private final boolean allowNonMatch;

    private final Map<Class<?>, A> registrations = new HashMap<>();

    private final Map<Class<?>, A> cache = new ConcurrentHashMap<>();

    /**
     * Used to identify types for which there is no matching adapter; we're using it
     * as if it were a ConcurrentSet.
     */
    private final Map<Class<?>, Boolean> unmatched = new ConcurrentHashMap<>();

    private StrategyRegistry(
        Class<A> adapterType,
        Map<Class<?>, A> registrations,
        boolean allowNonMatch) {
        this.adapterType = adapterType;
        this.allowNonMatch = allowNonMatch;

        this.registrations.putAll(registrations);
    }

    /**
     * Creates a strategy registry for the given adapter type. The registry will be
     * configured to require matches.
     *
     * @param adapterType
     *            the type of adapter retrieved from the registry
     * @param registrations
     *            map of registrations (the contents of the map are copied)
     */
    public static <A> StrategyRegistry<A> newInstance(
        Class<A> adapterType,
        Map<Class<?>, A> registrations) {
        return newInstance(adapterType, registrations, false);
    }

    /**
     * Creates a strategy registry for the given adapter type.
     *
     * @param adapterType
     *            the type of adapter retrieved from the registry
     * @param registrations
     *            map of registrations (the contents of the map are copied)
     * @param allowNonMatch
     *            if true, then the registry supports non-matches when retrieving an
     *            adapter
     */
    public static <A> StrategyRegistry<A> newInstance(
        Class<A> adapterType,
        Map<Class<?>, A> registrations,
        boolean allowNonMatch) {
        return new StrategyRegistry<A>(
            adapterType,
            registrations,
            allowNonMatch);
    }

    public void clearCache() {
        cache.clear();
        unmatched.clear();
    }

    public Class<A> getAdapterType() {
        return adapterType;
    }

    /**
     * Gets an adapter for an object. Searches based on the value's class, unless
     * the value is null, in which case, a search on class void is used.
     *
     * @param value
     *            for which an adapter is needed
     * @return the adapter for the value or null if not found (and allowNonMatch is
     *         true)
     * @throws IllegalArgumentException
     *             if no matching adapter may be found and allowNonMatch is false
     */

    public A getByInstance(Object value) {
        return get(value == null ? void.class : value.getClass());
    }

    /**
     * Searches for an adapter corresponding to the given input type.
     *
     * @param type
     *            the type to search
     * @return the adapter for the type or null if not found (and allowNonMatch is
     *         true)
     * @throws IllegalArgumentException
     *             if no matching adapter may be found and allowNonMatch is false
     */
    public A get(Class<?> type) {
        A result = cache.get(type);

        if (result != null)
            return result;

        if (unmatched.containsKey(type))
            return null;

        result = findMatch(type);

        // This may be null in the case that there is no match and we're allowing that
        // to not
        // be an error. That's why we check via containsKey.

        if (result != null) {
            cache.put(type, result);
        } else {
            unmatched.put(type, true);
        }

        return result;
    }

    private A findMatch(Class<?> type) {
        for (Class<?> t : new InheritanceSearch(type)) {
            A result = registrations.get(t);

            if (result != null)
                return result;
        }

        if (allowNonMatch)
            return null;

        // Report the error. These things really confused the hell out of people in
        // Tap4, so we're
        // going the extra mile on the exception message.

        var names = new ArrayList<String>();
        for (Class<?> t : registrations.keySet())
            names.add(t.getName());

        throw new UnknownValueException(
            String.format(
                "No adapter from type %s to type %s is available.",
                type.getName(),
                adapterType.getName()),
            null,
            registrations
                .keySet()
                .stream()
                .map(Object::toString)
                .sorted()
                .toList());
    }

    /**
     * Returns the registered types for which adapters are available.
     */
    public Collection<Class<?>> getTypes() {
        return new ArrayList<>(registrations.keySet());
    }

    @Override
    public String toString() {
        return String.format("StrategyRegistry[%s]", adapterType.getName());
    }
}
