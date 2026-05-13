package com.jujin.freeway.ioc.internal;

import com.jujin.freeway.ioc.config.*;
import com.jujin.freeway.ioc.property.*;
import com.jujin.freeway.ioc.threading.*;
import com.jujin.freeway.ioc.classpath.*;
import com.jujin.freeway.ioc.exception.*;
import com.jujin.freeway.ioc.internal.util.OneShotLock;
import com.jujin.freeway.ioc.RegistryShutdownHub;
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
        assert listener != null;

        lock.check();

        listeners.add(listener);
    }

    @Override
    public void addRegistryWillShutdownListener(Runnable listener) {
        assert listener != null;

        lock.check();

        preListeners.add(listener);
    }

    /**
     * Fires the {@link RegistryShutdownListener#registryDidShutdown()} method on
     * each listener. At the end, all the listeners are discarded.
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
