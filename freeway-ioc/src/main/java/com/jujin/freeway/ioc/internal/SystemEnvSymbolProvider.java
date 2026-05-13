package com.jujin.freeway.ioc.internal;

import com.jujin.freeway.ioc.symbol.SymbolProvider;

import java.util.Map;
import java.util.TreeMap;

/**
 * Provides <em>case insensitive</em> access to environment variables.
 * Environment variable symbols are prefixed with "env.".
 *
 */
public class SystemEnvSymbolProvider implements SymbolProvider {
    private final Map<String, String> symbols = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

    @Override
    public synchronized String valueForSymbol(String symbolName) {
        if (symbols.isEmpty()) {
            Map<String, String> env = System.getenv();

            for (Map.Entry<String, String> entry : env.entrySet()) {
                symbols.put("env." + entry.getKey(), entry.getValue());
            }
        }

        return symbols.get(symbolName);
    }
}
