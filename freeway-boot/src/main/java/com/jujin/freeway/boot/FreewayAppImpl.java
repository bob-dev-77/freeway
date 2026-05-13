package com.jujin.freeway.boot;

import com.jujin.freeway.ioc.Registry;

import java.util.Objects;

/**
 * Default implementation of {@link FreewayApp}.
 */
class FreewayAppImpl implements FreewayApp {

    private final Registry registry;

    FreewayAppImpl(Registry registry) {
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    @Override
    public Registry getRegistry() {
        return registry;
    }

    @Override
    public void shutdown() {
        try {
            registry.shutdown();
        } catch (Exception e) {
            // Log and continue — shutdown should not throw
        }
    }
}
