package com.jujin.freeway.boot.config;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * A {@link ConfigProvider} that parses command-line arguments.
 * <p>
 * Supports {@code --key=value} and {@code --key value} formats.
 */
public class CommandLineArgsProvider implements ConfigProvider {

    private final Map<String, String> args = new LinkedHashMap<>();
    private static final int PRIORITY = 100;

    public CommandLineArgsProvider(String... args) {
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg.startsWith("--")) {
                String raw = arg.substring(2);
                int eqIdx = raw.indexOf('=');
                if (eqIdx > 0) {
                    this.args.put(
                        raw.substring(0, eqIdx),
                        raw.substring(eqIdx + 1)
                    );
                } else if (
                    i + 1 < args.length && !args[i + 1].startsWith("--")
                ) {
                    this.args.put(raw, args[++i]);
                } else {
                    this.args.put(raw, "true");
                }
            }
        }
    }

    @Override
    public boolean containsKey(String key) {
        return args.containsKey(key);
    }

    @Override
    public String getValue(String key) {
        return args.get(key);
    }

    @Override
    public Set<String> keys() {
        return args.keySet();
    }

    @Override
    public int priority() {
        return PRIORITY;
    }
}
