package com.jujin.freeway.ioc.internal;

import com.jujin.freeway.ioc.symbol.SymbolProvider;
import com.jujin.freeway.ioc.symbol.SymbolSource;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class SymbolSourceImpl implements SymbolSource {
    private final List<SymbolProvider> providers;

    /**
     * Cache of symbol name to fully expanded symbol value. Bounded; clears
     * itself if the symbol space grows unexpectedly large.
     */
    private final Map<String, String> cache = new ConcurrentHashMap<>();
    private static final int MAX_CACHE_SIZE = 2048;

    /**
     * Contains execution data needed when performing an expansion (largely, to
     * check for endless recursion).
     */
    private class SymbolExpansion {
        private final LinkedList<String> expandingSymbols = new LinkedList<>();

        String expandSymbols(String input) {
            StringBuilder builder = null;

            int startx = 0;

            while (true) {
                int symbolx = input.indexOf("${", startx);

                // Special case: if the string contains no symbols then return it as is.

                if (startx == 0 && symbolx < 0)
                    return input;

                // The string has at least one symbol, so its OK to create the StringBuilder

                if (builder == null)
                    builder = new StringBuilder();

                // No more symbols found, so add in the rest of the string.

                if (symbolx < 0) {
                    builder.append(input.substring(startx));
                    break;
                }

                builder.append(input.substring(startx, symbolx));

                int endx = input.indexOf("}", symbolx);

                if (endx < 0) {
                    String message = expandingSymbols.isEmpty()
                        ? String.format("Input string '%s' is missing a symbol closing brace.", input)
                        : String.format("Input string '%s' is missing a symbol closing brace (in %s).", input, path());

                    throw new RuntimeException(message);
                }

                String symbolName = input.substring(symbolx + 2, endx);

                builder.append(valueForSymbol(symbolName));

                // Restart the search after the '}'

                startx = endx + 1;
            }

            return builder.toString();
        }

        String valueForSymbol(String symbolName) {
            String value = cache.get(symbolName);

            if (value == null) {
                value = expandSymbol(symbolName);

                if (cache.size() >= MAX_CACHE_SIZE) {
                    cache.clear();
                }
                cache.put(symbolName, value);
            }

            return value;
        }

        String expandSymbol(String symbolName) {
            if (expandingSymbols.contains(symbolName)) {
                expandingSymbols.add(symbolName);
                throw new RuntimeException(String.format("Symbol '%s' is defined in terms of itself (%s).",
                    symbolName,
                    pathFrom(symbolName)));
            }

            expandingSymbols.addLast(symbolName);

            String value = null;

            for (SymbolProvider provider : providers) {
                value = provider.valueForSymbol(symbolName);

                if (value != null)
                    break;
            }

            if (value == null) {

                String message = expandingSymbols.size() == 1
                    ? String.format("Symbol '%s' is not defined.", symbolName)
                    : String.format("Symbol '%s' is not defined (in %s).", symbolName, path());

                throw new RuntimeException(message);
            }

            // The value may have symbols that need expansion.

            String result = expandSymbols(value);

            // And we're done expanding this symbol

            expandingSymbols.removeLast();

            return result;

        }

        String path() {
            StringBuilder builder = new StringBuilder();

            boolean first = true;

            for (String symbolName : expandingSymbols) {
                if (!first)
                    builder.append(" --> ");

                builder.append(symbolName);

                first = false;
            }

            return builder.toString();
        }

        String pathFrom(String startSymbolName) {
            StringBuilder builder = new StringBuilder();

            boolean first = true;
            boolean match = false;

            for (String symbolName : expandingSymbols) {
                if (!match) {
                    if (symbolName.equals(startSymbolName))
                        match = true;
                    else
                        continue;
                }

                if (!first)
                    builder.append(" --> ");

                builder.append(symbolName);

                first = false;
            }

            return builder.toString();
        }
    }

    public SymbolSourceImpl(final List<SymbolProvider> providers) {
        this.providers = new ArrayList<>(Objects.requireNonNull(providers, "providers"));
    }

    @Override
    public String expandSymbols(String input) {
        return new SymbolExpansion().expandSymbols(input);
    }

    @Override
    public String valueForSymbol(String symbolName) {
        String value = cache.get(symbolName);

        // If already in the cache, then return it. Otherwise, let the SE find the value
        // and
        // update the cache.

        return value != null ? value : new SymbolExpansion().valueForSymbol(symbolName);
    }

}
