package com.jujin.freeway.ioc.internal;

import com.jujin.freeway.ioc.Registry;
import com.jujin.freeway.ioc.internal.util.ExceptionUtils;
import com.jujin.freeway.ioc.internal.util.OneShotLock;
import java.util.List;
import org.slf4j.Logger;

/**
 * Startup service for Freeway IoC: automatically invoked at
 * {@linkplain Registry#performRegistryStartup() registry startup} to execute a
 * series of operations, via its ordered configuration of Runnable objects.
 */
public class RegistryStartup implements Runnable {

    private final Logger logger;

    private final List<Runnable> configuration;

    private final OneShotLock lock = new OneShotLock();

    public RegistryStartup(Logger logger, final List<Runnable> configuration) {
        this.logger = logger;
        this.configuration = configuration;
    }

    /**
     * Invokes run() on each contributed object. If the object throws a runtime
     * exception, it is logged but startup continues anyway. This method may only be
     * {@linkplain OneShotLock invoked once}.
     */
    @Override
    public void run() {
        lock.lock();

        // Do we want extra exception catching here?

        for (Runnable r : configuration) {
            try {
                r.run();
            } catch (RuntimeException ex) {
                // startup-failure=An exception occurred during startup: %s

                logger.error(
                    "An exception occurred during startup: {}",
                    ExceptionUtils.toMessage(ex),
                    ex
                );
            }
        }

        // We don't need them any more since this method can only be run once. It's a
        // insignificant
        // savings, but still a nice thing to do.

        configuration.clear();
    }
}
