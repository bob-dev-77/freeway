package com.jujin.freeway.boot.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages a chain of {@link ConfigSource} instances.
 * <p>
 * Sources are sorted by priority (lower first). When merging, higher-priority
 * sources override lower-priority ones.
 */
public class ConfigSources {

    private final List<ConfigSource> sources = new ArrayList<>();

    /**
     * Adds a configuration source. Sources are sorted by priority automatically.
     */
    public void add(ConfigSource source) {
        sources.add(source);
        Collections.sort(sources);
    }

    /**
     * Returns all registered sources (sorted by priority, highest precedence
     * first).
     */
    public List<ConfigSource> getSources() {
        return Collections.unmodifiableList(sources);
    }

    /**
     * Merges all sources into a single flat map. Higher-priority (lower priority
     * number) values override lower-priority ones.
     */
    public Map<String, String> merge() {
        Map<String, String> result = new LinkedHashMap<>();
        // Iterate in reverse order so that higher-priority sources override
        for (int i = sources.size() - 1; i >= 0; i--) {
            ConfigSource source = sources.get(i);
            for (String key : source.keys()) {
                String value = source.getValue(key);
                if (value != null) {
                    result.put(key, value);
                }
            }
        }
        return result;
    }

    /**
     * Returns the value for a key from the highest-priority source that has it.
     */
    public String getValue(String key) {
        for (ConfigSource source : sources) {
            if (source.containsKey(key)) {
                return source.getValue(key);
            }
        }
        return null;
    }

    /**
     * Returns true if any source contains the key.
     */
    public boolean containsKey(String key) {
        for (ConfigSource source : sources) {
            if (source.containsKey(key)) {
                return true;
            }
        }
        return false;
    }
}
