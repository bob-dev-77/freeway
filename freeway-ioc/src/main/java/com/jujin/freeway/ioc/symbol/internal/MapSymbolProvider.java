package com.jujin.freeway.ioc.symbol.internal;

import com.jujin.freeway.ioc.symbol.SymbolProvider;
import java.util.Map;

/**
 * Provides symbol values from a Map of symbol names and symbol values
 * (typically provided by a Freeway IOC service configuration).
 */
public class MapSymbolProvider implements SymbolProvider {

    private final Map<String, String> configuration;

    public MapSymbolProvider(final Map<String, String> configuration) {
        this.configuration = configuration;
    }

    @Override
    public String lookup(String symbolName) {
        return configuration.get(symbolName);
    }
}
