package com.jujin.freeway.ioc.internal;

import com.jujin.freeway.ioc.config.*;
import com.jujin.freeway.ioc.property.*;
import com.jujin.freeway.ioc.threading.*;
import com.jujin.freeway.ioc.classpath.*;
import com.jujin.freeway.ioc.exception.*;
import com.jujin.freeway.ioc.symbol.SymbolProvider;

/**
 * Obtains symbol values from JVM System properties. This implementation is
 * usually ordered first, so that explicit overrides, provided as JVM system
 * properties, can take effect.
 */
public class SystemPropertiesSymbolProvider implements SymbolProvider {
    @Override
    public String valueForSymbol(String symbolName) {
        return System.getProperty(symbolName);
    }
}
