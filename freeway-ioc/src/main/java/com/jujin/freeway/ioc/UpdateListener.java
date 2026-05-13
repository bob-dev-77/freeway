package com.jujin.freeway.ioc;

/**
 * Interface for objects which can periodically check for updates.
 * <p>
 * Note that this interface has moved from module freeway-core to freeway-ioc,
 * but has kept the same package (for backwards compatibility reasons).
 *
 * @see com.jujin.freeway.ioc.UpdateListenerHub
 */
public interface UpdateListener {
    /**
     * Invoked to force the receiver to check for updates to whatever underlying
     * resources it makes use of.
     */
    void checkForUpdates();
}
