package com.jujin.freeway.ioc.internal;

import com.jujin.freeway.ioc.RegistryShutdownHub;
import com.jujin.freeway.ioc.internal.util.OneShotLock;
import org.slf4j.Logger;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;

public class RegistryShutdownHubImpl implements RegistryShutdownHub {
    private final OneShotLock lock = new OneShotLock();

    private final Logger logger;

    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();

    private final List<Runnable> preListeners = new CopyOnWriteArrayList<>();

    public RegistryShutdownHubImpl(Logger logger) {
        this.logger = logger;
    }

    @Override
    public void addRegistryShutdownListener(Runnable listener) {
        if (listener == null) {
            throw new IllegalArgumentException("listener must not be null");
        }
        lock.check();
        listeners.add(listener);
    }

    @Override
    public void addRegistryWillShutdownListener(Runnable listener) {
        if (listener == null) {
            throw new IllegalArgumentException("listener must not be null");
        }
        lock.check();
        preListeners.add(listener);
    }

    /**
     * Fires all registered shutdown listeners in two phases:
     * <ol>
     *   <li>First, all "will shutdown" listeners (added via {@link #addRegistryWillShutdownListener(Runnable)})</li>
     *   <li>Then, all "did shutdown" listeners (added via {@link #addRegistryShutdownListener(Runnable)})</li>
     * </ol>
     *
     * <p><b>Error handling:</b> If a listener throws a RuntimeException, it is logged
     * but execution continues with the next listener. This ensures that one failing
     * listener does not prevent other listeners from running.
     *
     * <p><b>Important:</b> Listeners should NOT depend on the success of previous listeners.
     * Each listener must be independently safe to execute, as there is no guaranteed
     * rollback or compensation mechanism if a prior listener fails.
     *
     * <p>After all listeners have been executed (or attempted), the listener lists are cleared.
     */
    public void fireRegistryDidShutdown() {
        lock.lock();

        Stream.concat(preListeners.stream(), listeners.stream()).forEach(element -> {
            try {
                element.run();
            } catch (RuntimeException ex) {
                logger.error(ServiceMessages.shutdownListenerError(element, ex), ex);
            }
        });

        preListeners.clear();
        listeners.clear();
    }

}
