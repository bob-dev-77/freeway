package com.jujin.freeway.boot.config;

import java.util.Set;

/**
 * A source of configuration key-value pairs with an associated priority.
 * <p>
 * Lower priority values mean higher precedence.
 */
public interface ConfigSource extends Comparable<ConfigSource> {

    /** Returns true if this source contains the given key. */
    boolean containsKey(String key);

    /** Returns the value for the given key, or null if not present. */
    String getValue(String key);

    /** Returns all keys available in this source. */
    Set<String> keys();

    /** Priority value; lower values have higher precedence. */
    int priority();

    @Override
    default int compareTo(ConfigSource other) {
        return Integer.compare(this.priority(), other.priority());
    }
}
