package com.jujin.freeway.ioc.symbol;

import com.jujin.freeway.ioc.annotations.UsesOrderedConfiguration;
import com.jujin.freeway.ioc.annotations.Value;

/**
 * Used to manage <em>symbols</em>, configuration properties whose value is
 * evaluated at runtime. Symbols use the Ant syntax: <code>${foo.bar.baz}</code>
 * where <code>foo.bar.baz</code> is the name of the symbol. The symbol may
 * appear inside some annotation, such as {@link Value}.
 * <p>
 * The SymbolSource service configuration is an ordered list of
 * {@link com.jujin.freeway.ioc.symbol.SymbolProvider}s. Two key SymbolProvider
 * services are FactoryDefaults and ApplicationDefaults.
 */
@UsesOrderedConfiguration(SymbolProvider.class)
public interface SymbolSource {
    /**
     * Expands the value for a particular symbol. This may involve recursive
     * expansion, if the immediate value for the symbol itself contains symbols.
     *
     * @param symbolName
     * @return the expanded string
     * @throws RuntimeException
     *             if the symbol name can not be expanded (no {@link SymbolProvider}
     *             can provide its value), or if an expansion is directly or
     *             indirectly recursive
     */
    String valueForSymbol(String symbolName);

    /**
     * Given an input string that <em>may</em> contain symbols, returns the string
     * with any and all symbols fully expanded.
     *
     * @param input
     * @return expanded input
     */
    String expandSymbols(String input);
}
