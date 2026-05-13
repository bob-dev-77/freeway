package com.jujin.freeway.ioc.lifecycle;

/**
 * Interface used to encapsulate any strategy used defer the creation of some
 * object until just as needed.
 */
@FunctionalInterface
public interface ObjectCreator<T> {
    /**
     * Create and return the object. In some limited circumstances, the
     * implementation may cache the result, returning the same object for repeated
     * calls.
     */
    T create();
}
