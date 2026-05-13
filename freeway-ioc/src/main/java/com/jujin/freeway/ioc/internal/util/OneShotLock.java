package com.jujin.freeway.ioc.internal.util;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Logic for handling one shot semantics for classes; classes that include a
 * method (or methods) that "locks down" the instance, to prevent it from being
 * used again in the future.
 */
public class OneShotLock {

    private final AtomicBoolean lock = new AtomicBoolean();

    /**
     * Checks to see if the lock has been set (via {@link #lock()}).
     *
     * @throws IllegalStateException
     *             if the lock is set
     */
    public void check() {
        if (lock.get()) {
            // The depth to find the caller of the check() or lock() method varies between
            // JDKs.

            StackTraceElement[] elements = Thread.currentThread().getStackTrace();

            int i = 0;
            while (!elements[i].getMethodName().equals("check")) {
                i++;
            }

            throw new IllegalStateException(
                UtilMessages.oneShotLock(elements[i + 1]));
        }
    }

    /**
     * Checks the lock, then sets it.
     */
    public void lock() {
        check();

        if (!lock.compareAndSet(false, true)) {
            throw new IllegalStateException(
                UtilMessages.oneShotLock(
                    Thread.currentThread().getStackTrace()[1]));
        }
    }
}
