package com.jujin.freeway.ioc.advisor;

import java.util.Map;

/**
 * A service implementation builder that operates around a
 * {@link StrategyRegistry}, implementing a version of the Gang of Four Strategy
 * pattern.
 * <p>
 * The constructed service is configured with a number of adapters (that
 * implement the same service interface). Method invocations on the service are
 * routed to one of the adapters.
 * <p>
 * The first parameter of each method is used to select the appropriate adapter.
 * <p>
 * The ideal interface for use with this builder has only one method.
 */
public interface StrategyBuilder {
    /**
     * Given a number of adapters implementing the service interface, builds a
     * "dispatcher" implementations that delegates to the one of the adapters. It is
     * an error if any of the methods takes no parameters.
     *
     * @param <S>
     *            the service interface type
     * @param registry
     *            defines the adapters based on parameter type (of the first
     *            parameter)
     * @return a service implementation
     */
    <S> S build(StrategyRegistry<S> registry);

    /**
     * @param registrations
     *            map frm class to the adapter type
     * @param <S>
     * @return the dispatcher
     */
    <S> S build(Class<S> adapterType, Map<Class<?>, S> registrations);
}
