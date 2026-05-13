package com.jujin.freeway.web;

import com.jujin.freeway.ioc.ModuleProvider;

/**
 * SPI auto-discovery for {@link WebModule}.
 *
 * <p>
 * Registered in {@code META-INF/services/com.jujin.freeway.ioc.ModuleProvider}
 * so that {@code Registry.Builder.spiAndBuild()} automatically includes the Web
 * module.
 * </p>
 */
public class WebModuleProvider implements ModuleProvider {

    @Override
    public Class<?>[] modules() {
        return new Class<?>[] { WebModule.class };
    }
}
