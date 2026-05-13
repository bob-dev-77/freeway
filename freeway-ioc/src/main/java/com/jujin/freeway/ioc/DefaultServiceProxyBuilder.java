package com.jujin.freeway.ioc;

/**
 * Creates default implementations of a class.
 *
 */
public interface DefaultServiceProxyBuilder {
    /**
     * Creates a new implementation of the provided interface. Each method in the
     * interface will be implemented as a noop method. The method will ignore any
     * parameters and return null, or 0, or false (or return nothing if the method
     * is void).
     *
     * @param <S>
     * @param serviceInterface
     * @return implementation of service interface
     */
    <S> S createDefaultImplementation(Class<S> serviceInterface);
}
