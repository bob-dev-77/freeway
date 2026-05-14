package com.jujin.freeway.boot.config;

import java.util.Set;

/**
 * A single source of configuration key-value pairs with an associated priority.
 * <p>
 * Implementations read from a specific origin such as CLI arguments, JSON
 * files, properties files, or environment variables.
 * <p>
 * Lower priority values mean higher precedence. Multiple providers are
 * aggregated by {@link ConfigSource}.
 *
 * @see ConfigSource
 */
public interface ConfigProvider extends Comparable<ConfigProvider> {
    /** Returns true if this source contains the given key. */
    boolean containsKey(String key);

    /** Returns the value for the given key, or null if not present. */
    String getValue(String key);

    /** Returns all keys available in this source. */
    Set<String> keys();

    /** Priority value; lower values have higher precedence. */
    int priority();

    @Override
    default int compareTo(ConfigProvider other) {
        return Integer.compare(this.priority(), other.priority());
    }
}
