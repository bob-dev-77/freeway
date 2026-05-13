package com.jujin.freeway.ioc.lifecycle;

import com.jujin.freeway.ioc.annotations.EagerLoad;

/**
 * Optional interface that may be implemented by a service implementation (or
 * even a {@linkplain ServiceLocator#proxy(Class, Class) proxy} to give the
 * service implementation more control over its lifecyle.
 *
 */
public interface ReloadAware {
    /**
     * Invoked when Freeway {@linkplain UpdateListenerHub#fireCheckForUpdates()
     * notices that the implementation class has changed}. The existing instance is
     * notified, so that it can cleanly shutdown now, before being re-instantiated.
     * This is necessary when the service implementation retains some form of
     * external resources.
     * <p>
     * In addition, the implementation may request an immediate reload. Normally,
     * reloading of the service is deferred until a method of the proxy object is
     * invoked (this causes the normal just-in-time instantiation of the
     * implementation). When this method returns true, the implementation is
     * re-created immediately. This is most often the case for services that are
     * {@linkplain EagerLoad eagerly loaded} in the first place.
     *
     * @return true if the service should be reloaded immediately, false if reload
     *         should be deferred
     */
    boolean shutdownImplementationForReload();
}
