package com.jujin.freeway.ioc.symbol;

import com.jujin.freeway.ioc.annotations.UsesMappedConfiguration;

/**
 * A provider of values for symbols, used by the
 * {@link com.jujin.freeway.ioc.symbol.SymbolSource} service.
 * <p>
 * This is the service interface for the FactoryDefaults and ApplicationDefaults
 * services; each of these takes a configuration mapping symbols to their
 * values.
 *
 * @see FactoryDefaults
 * @see ApplicationDefaults
 */
@UsesMappedConfiguration(String.class)
public interface SymbolProvider {
    /**
     * Returns the value for the symbol, or null if this provider can not provide a
     * value. The value itself may contain symbols that will be recursively
     * expanded.
     *
     * @param symbolName
     * @return the value or null
     */
    String valueForSymbol(String symbolName);
}
