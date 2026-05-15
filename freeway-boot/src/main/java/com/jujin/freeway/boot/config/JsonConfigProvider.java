package com.jujin.freeway.boot.config;

import com.jujin.freeway.commons.json.JSONArray;
import com.jujin.freeway.commons.json.JSONObject;
import com.jujin.freeway.commons.json.JSONUtils;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * A {@link ConfigProvider} backed by a {@code .json} file.
 * <p>
 * Parses a flat or nested JSON object and flattens it into dot-separated
 * property keys (e.g., {@code server.port}). Uses the built-in
 * {@link JSONUtils} from freeway-commons — no external parsing library needed.
 *
 * <pre>{@code
 * {
 *   "server": {
 *     "port": 8080,
 *     "host": "0.0.0.0"
 *   }
 * }
 * }</pre>
 * flattens to:
 * <pre>{@code
 * server.port = 8080
 * server.host = 0.0.0.0
 * }</pre>
 */
public class JsonConfigProvider implements ConfigProvider {

    private final Map<String, String> props = new LinkedHashMap<>();
    private final int priority;

    /**
     * @param inputStream
     *            the input stream to read JSON from (will be closed)
     * @param priority
     *            priority value (lower = higher precedence)
     */
    public JsonConfigProvider(InputStream inputStream, int priority) {
        this.priority = priority;
        try (inputStream) {
            String content = new String(
                inputStream.readAllBytes(),
                StandardCharsets.UTF_8
            );
            Object parsed = JSONUtils.fromJson(content);
            if (parsed instanceof JSONObject obj) {
                flatten("", obj, props);
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load application.json: " + e.getMessage(), e);
        }
    }

    /** Recursively flatten a JSONObject into dotted keys. */
    private static void flatten(
        String prefix,
        JSONObject obj,
        Map<String, String> target
    ) {
        for (String key : obj.keySet()) {
            String fullKey = prefix.isEmpty() ? key : prefix + "." + key;
            Object value = obj.get(key);
            if (value instanceof JSONObject child) {
                flatten(fullKey, child, target);
            } else if (value instanceof JSONArray array) {
                // Flatten arrays as key.0=value, key.1=value, etc.
                for (int i = 0; i < array.size(); i++) {
                    Object item = array.get(i);
                    if (item != null) {
                        target.put(fullKey + "." + i, item.toString());
                    }
                }
            } else if (value != null) {
                target.put(fullKey, value.toString());
            }
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
