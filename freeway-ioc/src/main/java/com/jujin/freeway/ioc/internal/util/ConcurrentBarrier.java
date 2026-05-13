package com.jujin.freeway.ioc.internal.util;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;

/**
 * A barrier used to execute code in a context where it is guarded by read/write
 * locks. In addition, handles upgrading read locks to write locks (and vice
 * versa). Execution of code within a lock is in terms of a {@link Runnable}
 * object (that returns no value), or a {@link Supplier} object (which does
 * return a value).
 */
public class ConcurrentBarrier {
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    /**
     * ReentrantReadWriteLock provides
     * {@link ReentrantReadWriteLock#getReadHoldCount()} to check whether the
     * current thread holds the read lock, eliminating the need for a separate
     * ThreadLocal.
     */

    /**
     * Invokes the object after acquiring the read lock (if necessary). If invoked
     * when the read lock has not yet been acquired, then the lock is acquired for
     * the duration of the call. If the lock has already been acquired, then the
     * status of the lock is not changed.
     * <p>
     * TODO: Check to see if the write lock is acquired and <em>not</em> acquire the
     * read lock in that situation. Currently this code is not re-entrant. If a
     * write lock is already acquired and the thread attempts to get the read lock,
     * then the thread will hang. For the moment, all the uses of ConcurrentBarrier
     * are coded in such a way that reentrant locks are not a problem.
     *
     * @param <T>
     * @param invokable
     * @return the result of invoking the invokable
     */
    public <T> T withRead(Supplier<T> invokable) {
        boolean readLockedAtEntry = lock.getReadHoldCount() > 0;

        if (!readLockedAtEntry) {
            lock.readLock().lock();
        }

        try {
            return invokable.get();
        } finally {
            if (!readLockedAtEntry) {
                lock.readLock().unlock();
            }
        }
    }

    /**
     * As with {@link #withRead(Supplier)}, creating a {@link Supplier} wrapper
     * around the runnable object.
     */
    public void withRead(final Runnable runnable) {
        Supplier<Void> invokable = () -> {
            runnable.run();

            return null;
        };

        withRead(invokable);
    }

    /**
     * Acquires the exclusive write lock before invoking the Supplier. The code will
     * be executed exclusively, no other reader or writer threads will exist (they
     * will be blocked waiting for the lock). If the current thread has a read lock,
     * it is released before attempting to acquire the write lock, and re-acquired
     * after the write lock is released. Note that in that short window, between
     * releasing the read lock and acquiring the write lock, it is entirely possible
     * that some other thread will sneak in and do some work, so the
     * {@link Supplier} object should be prepared for cases where the state has
     * changed slightly, despite holding the read lock. This usually manifests as
     * race conditions where either a) some parallel unrelated bit of work has
     * occured or b) duplicate work has occured. The latter is only problematic if
     * the operation is very expensive.
     *
     * @param <T>
     * @param invokable
     */
    public <T> T withWrite(Supplier<T> invokable) {
        boolean readLockedAtEntry = releaseReadLock();

        lock.writeLock().lock();

        try {
            return invokable.get();
        } finally {
            lock.writeLock().unlock();
            restoreReadLock(readLockedAtEntry);
        }
    }

    private boolean releaseReadLock() {
        boolean readLockedAtEntry = lock.getReadHoldCount() > 0;

        if (readLockedAtEntry) {
            lock.readLock().unlock();
        }

        return readLockedAtEntry;
    }

    private void restoreReadLock(boolean readLockedAtEntry) {
        if (readLockedAtEntry) {
            lock.readLock().lock();
        }
    }

    /**
     * As with {@link #withWrite(Supplier)}, creating a {@link Supplier} wrapper
     * around the runnable object.
     */
    public void withWrite(final Runnable runnable) {
        Supplier<Void> invokable = () -> {
            runnable.run();

            return null;
        };

        withWrite(invokable);
    }

    /**
     * Try to aquire the exclusive write lock and invoke the Runnable. If the write
     * lock is obtained within the specfied timeout, then this method behaves as
     * {@link #withWrite(Runnable)} and will return true. If the write lock is not
     * obtained within the timeout then the runnable is never invoked and the method
     * will return false.
     *
     * @param runnable
     *            Runnable object to execute inside the write lock.
     * @param timeout
     *            Time to wait for write lock.
     * @param timeoutUnit
     *            Units of timeout.
     * @return true if lock was obtained and the runnable executed, or false
     *         otherwise.
     */
    public boolean tryWithWrite(final Runnable runnable, long timeout, TimeUnit timeoutUnit) {
        boolean readLockedAtEntry = releaseReadLock();

        boolean obtainedLock = false;

        try {
            try {
                obtainedLock = lock.writeLock().tryLock(timeout, timeoutUnit);

                if (obtainedLock)
                    runnable.run();

            } catch (InterruptedException e) {
                obtainedLock = false;
            } finally {
                if (obtainedLock)
                    lock.writeLock().unlock();
            }
        } finally {
            restoreReadLock(readLockedAtEntry);
        }

        return obtainedLock;
    }

}
