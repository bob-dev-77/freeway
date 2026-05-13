package com.jujin.freeway.db;

import com.jujin.freeway.ioc.ModuleProvider;

/**
 * SPI provider that registers {@link DbModule} for auto-discovery via
 * {@code com.jujin.freeway.ioc.ModuleProvider}.
 */
public class DbModuleProvider implements ModuleProvider {

    @Override
    public Class<?>[] modules() {
        return new Class<?>[] { DbModule.class };
    }
}
