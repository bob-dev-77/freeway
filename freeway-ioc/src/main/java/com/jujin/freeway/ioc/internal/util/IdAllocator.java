package com.jujin.freeway.ioc.internal.util;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Used to "uniquify" names within a given context. A base name is passed in,
 * and the return value is the base name, or the base name extended with a
 * suffix to make it unique.
 * <p>
 * This class is not threadsafe.
 */

public final class IdAllocator {

    private static final String SEPARATOR = "_";

    /**
     * Map from allocated id to a generator for names associated with the allocated
     * id.
     */
    private final Map<String, NameGenerator> generatorMap;

    private final String namespace;

    /**
     * Generates unique names with a particular prefix.
     */
    private static class NameGenerator implements Cloneable {

        private final String baseId;

        private int index;

        NameGenerator(String baseId) {
            this.baseId = baseId + SEPARATOR;
        }

        public String nextId() {
            return baseId + index++;
        }

        /**
         * Clones this instance, returning an equivalent but separate copy.
         */
        @SuppressWarnings({ "CloneDoesntDeclareCloneNotSupportedException" })
        @Override
        public NameGenerator clone() {
            try {
                return (NameGenerator) super.clone();
            } catch (CloneNotSupportedException ex) {
                // Unreachable!
                throw new RuntimeException(ex);
            }
        }
    }

    /**
     * Creates a new allocator with no namespace.
     */
    public IdAllocator() {
        this("");
    }

    /**
     * Creates a new allocator with the provided namespace.
     */
    public IdAllocator(String namespace) {
        this(namespace, new HashMap<String, NameGenerator>());
    }

    private IdAllocator(
        String namespace,
        Map<String, NameGenerator> generatorMap) {
        this.namespace = namespace;
        this.generatorMap = generatorMap;
    }

    /**
     * Returns a list of all allocated ids, sorted alphabetically.
     */
    public List<String> getAllocatedIds() {
        return InternalUtils.sortedKeys(generatorMap);
    }

    /**
     * Creates a clone of this IdAllocator instance, copying the allocator's
     * namespace and key map.
     */
    @SuppressWarnings({ "CloneDoesntCallSuperClone" })
    @Override
    public IdAllocator clone() {
        // Copying the generatorMap is tricky; multiple keys will point to the same
        // NameGenerator
        // instance. We need to clone the NameGenerators, then build a new map around
        // the clones.

        IdentityHashMap<NameGenerator, NameGenerator> transformMap = new IdentityHashMap<NameGenerator, NameGenerator>();

        for (NameGenerator original : generatorMap.values()) {
            NameGenerator copy = original.clone();

            transformMap.put(original, copy);
        }

        Map<String, NameGenerator> mapCopy = new HashMap<>();

        for (Map.Entry<String, NameGenerator> entry : generatorMap.entrySet()) {
            NameGenerator copy = transformMap.get(entry.getValue());

            mapCopy.put(entry.getKey(), copy);
        }

        return new IdAllocator(namespace, mapCopy);
    }

    /**
     * Allocates the id. Repeated calls for the same name will return "name",
     * "name_0", "name_1", etc.
     */
    public String allocateId(String name) {
        String key = name + namespace;

        NameGenerator g = generatorMap.get(key);
        String result;

        if (g == null) {
            g = new NameGenerator(key);
            result = key;
        } else
            result = g.nextId();

        // Handle the degenerate case, where a base name of the form "foo_0" has been
        // requested. Skip over any duplicates thus formed.

        while (generatorMap.containsKey(result))
            result = g.nextId();

        generatorMap.put(result, g);

        return result;
    }

    /**
     * Checks to see if a given name has been allocated.
     */
    public boolean isAllocated(String name) {
        return generatorMap.containsKey(name);
    }

    /**
     * Clears the allocator, resetting it to freshly allocated state.
     */
    public void clear() {
        generatorMap.clear();
    }
}
