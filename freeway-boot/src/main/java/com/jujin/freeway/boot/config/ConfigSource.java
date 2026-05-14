package com.jujin.freeway.boot.config;

import java.util.*;

/**
 * Aggregates multiple {@link ConfigProvider} instances into a unified view,
 * sorted by priority.
 * <p>
 * This is the public entry point for reading boot configuration — analogous to
 * {@code SymbolSource} in the IoC layer. Call {@link #add(ConfigProvider)} to
 * register sources, then {@link #merge()} or {@link #getValue(String)} to read
 * values.
 */
public class ConfigSource {

    private final List<ConfigProvider> providers = new ArrayList<>();

    /** Adds a configuration provider. Providers are sorted by priority. */
    public void add(ConfigProvider provider) {
        providers.add(provider);
        Collections.sort(providers);
    }

    /** Returns all registered providers (sorted by priority). */
    public List<ConfigProvider> getProviders() {
        return Collections.unmodifiableList(providers);
    }

    /**
     * Merges all providers into a single flat map. Higher-priority (lower priority
     * number) values override lower-priority ones.
     */
    public Map<String, String> merge() {
        Map<String, String> result = new LinkedHashMap<>();
        for (int i = providers.size() - 1; i >= 0; i--) {
            ConfigProvider source = providers.get(i);
            for (String key : source.keys()) {
                String value = source.getValue(key);
                if (value != null) {
                    result.put(key, value);
                }
            }
        }
        return result;
    }

    /** Returns the value from the highest-priority provider that has it. */
    public String getValue(String key) {
        for (ConfigProvider source : providers) {
            if (source.containsKey(key)) {
                return source.getValue(key);
            }
        }
        return null;
    }

    /** Returns true if any provider contains the key. */
    public boolean containsKey(String key) {
        for (ConfigProvider source : providers) {
            if (source.containsKey(key)) {
                return true;
            }
        }
        return false;
    }
}
