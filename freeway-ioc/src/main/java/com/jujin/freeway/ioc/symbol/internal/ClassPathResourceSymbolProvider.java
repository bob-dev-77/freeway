package com.jujin.freeway.ioc.symbol.internal;

import com.jujin.freeway.ioc.internal.util.ClassPathResource;

/**
 * Makes a {@link com.jujin.freeway.ioc.Resource} on the classpath available as
 * a {@link com.jujin.freeway.ioc.symbol.SymbolProvider}
 *
 */
public class ClassPathResourceSymbolProvider extends ResourceSymbolProvider {
    public ClassPathResourceSymbolProvider(String path) {
        super(new ClassPathResource(path));
    }
}
