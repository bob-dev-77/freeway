package com.jujin.freeway.ioc.threading;

import com.jujin.freeway.ioc.lifecycle.ObjectCreator;
import java.util.function.Supplier;

/**
 * Manages per-thread data, and provides a way for listeners to know when such
 * data should be cleaned up. Typically, data is cleaned up at the end of the
 * request (in a web application). Freeway IoC has any number of objects that
 * need to know when this event occurs, so that they can clean up any
 * per-thread/per-request state.
 */
public interface PerThreadManager {
    /**
     * Adds a callback to be invoked when {@link #cleanup()} is invoked; callbacks
     * are then removed.
     *
     * @param callback
     */
    void addThreadCleanupCallback(Runnable callback);

    /**
     * Immediately performs a cleanup of the thread, invoking all callback, then
     * discarding all per-thread data stored by the manager (including the list of
     * callbacks).
     */
    void cleanup();

    /**
     * Creates a value using a unique internal key.
     *
     */
    <T> PerThreadValue<T> createValue();

    /**
     * Return {@link ObjectCreator}, which for each thread, the first call will use
     * the delegate {@link ObjectCreator} to create an instance, and later calls
     * will reuse the same per-thread instance. The instance is stored in the
     * {@link com.jujin.freeway.ioc.PerthreadManager} and will be released at the
     * end of the request.
     *
     */
    <T> ObjectCreator<T> createValue(ObjectCreator<T> delegate);

    /**
     * Invokes {@link Runnable#run()}, providing a try...finally to
     * {@linkplain #cleanup() cleanup} after.
     *
     */
    void run(Runnable runnable);

    /**
     * Returns the result from the invocation, providing a try...finally to
     * {@linkplain #cleanup() cleanup} after.
     */
    <T> T invoke(Supplier<T> invokable);
}
