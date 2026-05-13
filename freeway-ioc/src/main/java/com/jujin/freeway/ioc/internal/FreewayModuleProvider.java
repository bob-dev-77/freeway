package com.jujin.freeway.ioc.internal;

import com.jujin.freeway.ioc.FreewayIOCModule;
import com.jujin.freeway.ioc.ModuleProvider;

/**
 * Built-in {@link ModuleProvider} that registers the core
 * {@link FreewayIOCModule}.
 *
 * <p>
 * Declared via {@code META-INF/services/com.jujin.freeway.ioc.ModuleProvider}
 * so it is always discovered by {@code Registry.Builder.autoDiscover()}.
 * </p>
 */
public final class FreewayModuleProvider implements ModuleProvider {

    @Override
    public Class<?>[] modules() {
        return new Class<?>[] { FreewayIOCModule.class };
    }
}
