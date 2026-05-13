package com.jujin.freeway.test.advisor;

import com.jujin.freeway.ioc.ModuleProvider;

/**
 * SPI-based {@link ModuleProvider} that registers the
 * {@link AdvisorOnlyTestModule}.
 *
 * <p>
 * Declared via {@code META-INF/services/com.jujin.freeway.ioc.ModuleProvider}
 * so it is discovered by {@code Registry.Builder.autoDiscover()}.
 * </p>
 */
public final class AdvisorModuleProvider implements ModuleProvider {

    @Override
    public Class<?>[] modules() {
        return new Class<?>[] { AdvisorOnlyTestModule.class };
    }
}
