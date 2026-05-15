package com.jujin.freeway.boot.config;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * A {@link ConfigProvider} backed by system environment variables.
 * <p>
 * Only exposes variables prefixed with a configurable prefix (default: {@code FREEWAY_}).
 * The prefix is stripped from the key (e.g., {@code FREEWAY_PROFILES_ACTIVE} →
 * {@code freeway.profiles.active}). Uses underscore-to-dot conversion and
 * lowercasing.
 * <p>
 * The prefix can be customized via system property {@code freeway.env.prefix}.
 */
public class EnvironmentConfigProvider implements ConfigProvider {

    private static final String DEFAULT_PREFIX = "FREEWAY_";
    private static final String PREFIX_PROPERTY = "freeway.env.prefix";
    
    private final String prefix;
    private final Map<String, String> env = new LinkedHashMap<>();
    private static final int PRIORITY = 200;

    public EnvironmentConfigProvider() {
        this(System.getProperty(PREFIX_PROPERTY, DEFAULT_PREFIX));
    }

    /**
     * @param prefix the environment variable prefix to filter on
     */
    public EnvironmentConfigProvider(String prefix) {
        this.prefix = prefix != null ? prefix : DEFAULT_PREFIX;
        for (Map.Entry<String, String> entry : System.getenv().entrySet()) {
            String key = entry.getKey();
            if (key.startsWith(this.prefix)) {
                String converted = key
                    .substring(this.prefix.length())
                    .toLowerCase()
                    .replace("_", ".");
                env.put(converted, entry.getValue());
            }
        }
    }

    @Override
    public boolean containsKey(String key) {
        return env.containsKey(key);
    }

    @Override
    public String getValue(String key) {
        return env.get(key);
    }

    @Override
    public Set<String> keys() {
        return env.keySet();
    }

    @Override
    public int priority() {
        return PRIORITY;
    }
}
