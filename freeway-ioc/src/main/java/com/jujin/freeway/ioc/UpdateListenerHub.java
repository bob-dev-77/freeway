package com.jujin.freeway.ioc;

/**
 * Manages a set of {@link com.jujin.freeway.ioc.UpdateListener}s. Periodically
 * (say, every request during development, or every minute or so during
 * production), request processing is locked down so that only a single thread
 * is active, and the active thread invokes {@link #fireCheckForUpdates()}.
 * Various services that are dependent on external resource files (such as
 * classes or template files) can check to see if any file they've used has
 * changed. If so, the service can invalidate its internal cache, or notify
 * other services (typically via
 * {@link com.jujin.freeway.ioc.InvalidationListener} that they should do the
 * same.
 * <p>
 * Note that this interface has moved from module freeway-core to freeway-ioc.
 * It was, however, not possible to keep the same package (for backwards
 * compatibility reasons) without causing a split package (in terms of Java 9
 * Modules).
 * <p>
 * A <em>weak reference</em> to the listener is kept; this ensures that
 * registering as a listener will not prevent a listener instance from being
 * reclaimed by the garbage collector (this is useful as proxies created by
 * {@link ServiceLocator#proxy(Class, Class)} may register as listeners, but
 * still be ephemeral).
 * <p>
 * Starting in Freeway 5.3, this services does <em>nothing</em> in production
 * mode.
 *
 */
public interface UpdateListenerHub {
    /**
     * Adds a listener.
     */
    void addUpdateListener(UpdateListener listener);

    /**
     * Invoked periodically to allow services to check if underlying state has
     * changed. For example, a template file may have changed. Listeners will
     * typically notify applicable listeners of their own (they usually implement
     * {@link com.jujin.freeway.ioc.InvalidationEventHub}) when such a change
     * occurs.
     */
    void fireCheckForUpdates();
}
