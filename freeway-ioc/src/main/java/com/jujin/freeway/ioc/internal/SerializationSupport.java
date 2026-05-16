package com.jujin.freeway.ioc.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.ref.WeakReference;

/**
 * Serialization support for service proxies.
 */
class SerializationSupport {
    private static final Logger LOGGER = LoggerFactory.getLogger(SerializationSupport.class);

    // We use a weak reference so that the underlying Registry can be reclaimed by
    // the garbage collector
    // even if it is not explicitly shut down.

    private static WeakReference<ServiceProxyProvider> providerRef;

    static synchronized void setProvider(ServiceProxyProvider proxyProvider) {
        ServiceProxyProvider existing = currentProvider();

        if (existing != null)
            LOGGER.error("Setting a new service proxy provider when there's already an existing provider. This may indicate that you have multiple IoC Registries.");

        providerRef = new WeakReference<ServiceProxyProvider>(proxyProvider);
    }

    // Only invoked from synchronized blocks
    private static ServiceProxyProvider currentProvider() {
        return providerRef == null ? null : providerRef.get();
    }

    static synchronized void clearProvider(ServiceProxyProvider proxyProvider) {
        ServiceProxyProvider existing = currentProvider();

        // The registry does a setProvider() at startup, and we want to make sure that
        // we're the only Registry, that
        // there hasn't been another setProvider() by another Registry.

        if (existing != proxyProvider) {
            LOGGER.error("Unexpected service proxy provider when clearing the provider. This may indicate that you have multiple IoC Registries.");
            return;
        }

        // Good. It's all the expected simple case, without duelling registries. Kill
        // the reference
        // to the registry.

        providerRef = null;
    }

    static synchronized Object readResolve(String serviceId) {
        ServiceProxyProvider provider = currentProvider();

        if (provider == null)
            throw new RuntimeException(
                String.format(
                    "Service token for service '%s' can not be converted back into a proxy because no proxy provider has been registered. This may indicate that an IoC Registry has not been started yet.",
                    serviceId));

        return provider.provideServiceProxy(serviceId);
    }

    static ServiceProxyToken createToken(String serviceId) {
        return new ServiceProxyToken(serviceId);
    }

}
