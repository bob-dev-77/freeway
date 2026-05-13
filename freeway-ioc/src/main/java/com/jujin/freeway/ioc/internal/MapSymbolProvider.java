package com.jujin.freeway.ioc.internal;

import com.jujin.freeway.ioc.config.*;
import com.jujin.freeway.ioc.property.*;
import com.jujin.freeway.ioc.threading.*;
import com.jujin.freeway.ioc.classpath.*;
import com.jujin.freeway.ioc.exception.*;
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
    public String valueForSymbol(String symbolName) {
        return configuration.get(symbolName);
    }

}
