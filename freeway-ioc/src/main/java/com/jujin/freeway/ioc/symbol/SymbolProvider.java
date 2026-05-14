package com.jujin.freeway.ioc.symbol;

import com.jujin.freeway.ioc.annotations.UsesMappedConfiguration;

/**
 * SPI for contributing raw symbol values to the {@link SymbolSource}.
 *
 * <p>
 * Implement this interface to provide values from a custom source (e.g.
 * environment variables, system properties, a map, a file). Each provider
 * is a <em>single source</em> of values; the {@link SymbolSource}
 * aggregates all providers into a unified view with caching and recursive
 * expansion.
 *
 * <p>
 * Application code should never call this method directly — use
 * {@link SymbolSource#resolve(String)} instead.
 *
 * @see SymbolSource
 * @see FactoryDefaults
 * @see ApplicationDefaults
 */
@UsesMappedConfiguration(String.class)
public interface SymbolProvider {
    /**
     * Looks up the raw value for a symbol from this provider.
     *
     * @param symbolName the symbol name (never {@code null})
     * @return the raw value, or {@code null} if this provider does not
     *         know about this symbol
     */
    String lookup(String symbolName);
}
