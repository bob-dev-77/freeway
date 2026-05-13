package com.jujin.freeway.boot.config;

import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * A {@link ConfigSource} backed by a {@code .yml} file.
 * <p>
 * Uses a simple recursive parser to convert flat YAML key-value pairs into
 * dot-separated property keys (e.g., {@code server.port}).
 */
public class YamlConfigSource implements ConfigSource {

    private final Map<String, String> props = new LinkedHashMap<>();
    private final int priority;

    /**
     * @param inputStream
     *            the input stream to read YAML from (will be closed)
     * @param priority
     *            priority value (lower = higher precedence)
     */
    public YamlConfigSource(InputStream inputStream, int priority) {
        this.priority = priority;
        try (inputStream) {
            String content = new String(inputStream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            parseYaml(content, "", props);
        } catch (Exception e) {
            // Ignore — empty source
        }
    }

    private void parseYaml(String content, String prefix, Map<String, String> target) {
        String[] lines = content.split("\n");
        Map<Integer, String> indentMap = new LinkedHashMap<>();

        for (String line : lines) {
            if (line.trim().isEmpty() || line.trim().startsWith("#"))
                continue;

            int indent = countLeadingSpaces(line);
            String trimmed = line.trim();

            if (trimmed.contains(":")) {
                int colonIdx = trimmed.indexOf(':');
                String key = trimmed.substring(0, colonIdx).trim();
                String value = trimmed.substring(colonIdx + 1).trim();

                // Remove higher-indent siblings
                indentMap.entrySet().removeIf(e -> e.getKey() > indent);

                indentMap.put(indent, key);

                if (value.isEmpty()) {
                    // This key is a parent — it will have children on the next lines
                    continue;
                }

                // Build full dotted key
                StringBuilder fullKey = new StringBuilder();
                for (Map.Entry<Integer, String> entry : indentMap.entrySet()) {
                    if (fullKey.length() > 0)
                        fullKey.append('.');
                    fullKey.append(entry.getValue());
                }

                // Remove surrounding quotes
                if ((value.startsWith("\"") && value.endsWith("\""))
                    || (value.startsWith("'") && value.endsWith("'"))) {
                    value = value.substring(1, value.length() - 1);
                }

                target.put(fullKey.toString(), value);
            }
        }
    }

    private int countLeadingSpaces(String s) {
        int count = 0;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == ' ')
                count++;
            else
                break;
        }
        return count;
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
