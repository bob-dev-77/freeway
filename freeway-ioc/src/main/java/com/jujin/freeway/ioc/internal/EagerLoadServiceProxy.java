package com.jujin.freeway.ioc.internal;

/**
 * Interface implemented by all service proxies. Service proxies are always
 * {@link com.jujin.freeway.ioc.RegistryShutdownListener}s, they also can be
 * eager-load
 */
public interface EagerLoadServiceProxy {
    void eagerLoadService();
}
