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
 * {@link SymbolProvider}s. Two key providers are FactoryDefaults and
 * ApplicationDefaults.
 * <p>
 * This is the <em>public API</em> for reading symbols. The lower-level
 * {@link SymbolProvider} SPI is for implementing custom symbol sources.
 */
@UsesOrderedConfiguration(SymbolProvider.class)
public interface SymbolSource {
    /**
     * Returns the fully expanded value for a symbol. Resolution spans all
     * configured {@link SymbolProvider}s, with caching and recursive
     * expansion of {@code ${...}} references.
     *
     * @param symbolName the symbol name (never {@code null})
     * @return the expanded value
     * @throws RuntimeException if the symbol is not defined in any provider
     */
    String resolve(String symbolName);

    /**
     * Checks whether a symbol is defined in any of the configured
     * {@link SymbolProvider}s.
     *
     * @param symbolName the symbol name
     * @return {@code true} if the symbol is defined, {@code false} otherwise
     */
    boolean contains(String symbolName);

    /**
     * Expands {@code ${...}} symbol references in the given input string.
     * Each symbol is resolved via {@link #resolve(String)} and replaced
     * inline.
     *
     * <p>Supports default value syntax: {@code ${symbol:default_value}}.
     * If the symbol is not defined, the default value is used instead.</p>
     *
     * <p>Examples:</p>
     * <pre>{@code
     * symbols.expand("Welcome to ${app.name}!");
     * // → "Welcome to MyApp!"
     *
     * symbols.expand("${undefined.symbol:fallback}");
     * // → "fallback"
     * }</pre>
     *
     * @param input string that may contain symbols
     * @return the input with all symbols expanded
     * @throws RuntimeException if a symbol is not defined and has no default value
     */
    String expand(String input);
}
