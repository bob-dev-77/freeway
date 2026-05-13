package com.jujin.freeway.boot;

import com.jujin.freeway.ioc.Registry;

/**
 * Application container for freeway-boot.
 * <p>
 * Wraps the IoC {@link Registry} lifecycle with external configuration loading
 * and graceful shutdown.
 */
public interface FreewayApp {

    /**
     * Returns the underlying IoC {@link Registry}.
     */
    Registry getRegistry();

    /**
     * Initiates a graceful shutdown of the application.
     */
    void shutdown();
}
