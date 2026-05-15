package com.jujin.freeway.boot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.jujin.freeway.ioc.Registry;

import java.util.Objects;

/**
 * Default implementation of {@link FreewayApp}.
 */
class FreewayAppImpl implements FreewayApp {

    private static final Logger LOG = LoggerFactory.getLogger(FreewayAppImpl.class);

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
            LOG.error("Registry shutdown failed - potential resource leak", e);
        }
    }
}
