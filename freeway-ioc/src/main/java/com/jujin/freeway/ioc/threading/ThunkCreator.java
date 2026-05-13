package com.jujin.freeway.ioc.threading;
import com.jujin.freeway.ioc.*;

import com.jujin.freeway.ioc.lifecycle.ObjectCreator;

/**
 * A <a href="http://en.wikipedia.org/wiki/Thunk">thunk</a> is a delayed
 * calculation. In Java and Freeway terms, a Thunk is a proxy object of a
 * particular interface that delegates all methods to an object of the same type
 * obtained from an {@link ServiceProvider}. This is used
 * by {@link com.jujin.freeway.ioc.advisor.LazyAdvisor} to build lazy thunk
 * proxies.
 *
 */
public interface ThunkCreator {
    /**
     * Creates a Thunk of the given proxy type.
     *
     * @param proxyType
     *            type of object to create (must be an interface)
     * @param objectCreator
     *            provides an instance of the same type on demand (may be invoked
     *            multiple times)
     * @param description
     *            to be returned from the thunk's toString() method
     * @param <T>
     *            type of thunk
     * @return thunk of given type
     */
    @SuppressWarnings("rawtypes")
    <T> T createThunk(
        Class<T> proxyType,
        ObjectCreator objectCreator,
        String description);
}
