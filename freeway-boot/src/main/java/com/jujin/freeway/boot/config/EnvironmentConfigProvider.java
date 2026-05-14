package com.jujin.freeway.boot.config;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * A {@link ConfigProvider} backed by system environment variables.
 * <p>
 * Only exposes variables prefixed with {@code FREEWAY_}. The prefix is stripped
 * from the key (e.g., {@code FREEWAY_PROFILES_ACTIVE} →
 * {@code freeway.profiles.active}). Uses underscore-to-dot conversion and
 * lowercasing.
 */
public class EnvironmentConfigProvider implements ConfigProvider {

    private static final String PREFIX = "FREEWAY_";
    private final Map<String, String> env = new LinkedHashMap<>();
    private static final int PRIORITY = 200;

    public EnvironmentConfigProvider() {
        for (Map.Entry<String, String> entry : System.getenv().entrySet()) {
            String key = entry.getKey();
            if (key.startsWith(PREFIX)) {
                String converted = key
                    .substring(PREFIX.length())
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
