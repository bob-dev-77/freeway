package com.jujin.freeway.boot.config;

import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * A {@link ConfigProvider} backed by a {@code .properties} file.
 */
public class PropertiesConfigProvider implements ConfigProvider {

    private final Map<String, String> props = new LinkedHashMap<>();
    private final int priority;

    /**
     * @param inputStream
     *            the input stream to read properties from (will be closed)
     * @param priority
     *            priority value (lower = higher precedence)
     */
    public PropertiesConfigProvider(InputStream inputStream, int priority) {
        this.priority = priority;
        try (inputStream) {
            Properties p = new Properties();
            p.load(inputStream);
            for (String key : p.stringPropertyNames()) {
                props.put(key, p.getProperty(key));
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load application.properties: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean containsKey(String key) {
        return props.containsKey(key);
    }

    @Override
    public String getValue(String key) {
        return props.get(key);
    }

    @Override
    public Set<String> keys() {
        return props.keySet();
    }

    @Override
    public int priority() {
        return priority;
    }
}
