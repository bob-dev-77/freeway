package com.jujin.freeway.ioc;

/**
 * Event hub for notifications when the IOC
 * {@link com.jujin.freeway.ioc.Registry} shuts down.
 */
public interface RegistryShutdownHub {
    /**
     * Adds a listener for eventual notification when the registry shuts down.
     * Runtime exceptions thrown by the listener will be logged and ignored.
     *
     */
    void addRegistryShutdownListener(Runnable listener);

    /**
     * Adds a listener for eventual notification. RegistryWillShutdownListeners are
     * notified before any standard listeners, and before service proxies and other
     * parts of the Registry are disabled. Runtime exceptions thrown by the listener
     * will be logged and ignored.
     *
     */
    void addRegistryWillShutdownListener(Runnable listener);
}
