package com.jujin.freeway.boot.test;

import com.jujin.freeway.ioc.ModuleProvider;

/**
 * SPI ModuleProvider that auto-discovers the TestBootApp module class. This
 * allows {@code builder.autoDiscover()} to find it.
 */
public class TestBootAppProvider implements ModuleProvider {

    @Override
    public Class<?>[] modules() {
        return new Class<?>[] { TestBootApp.class };
    }
}
