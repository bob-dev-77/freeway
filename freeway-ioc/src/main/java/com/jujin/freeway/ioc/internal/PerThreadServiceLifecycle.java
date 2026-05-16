package com.jujin.freeway.ioc.internal;

import com.jujin.freeway.ioc.ServiceContext;
import com.jujin.freeway.ioc.annotations.Builtin;
import com.jujin.freeway.ioc.lifecycle.ObjectCreator;
import com.jujin.freeway.ioc.lifecycle.PerThreadManager;
import com.jujin.freeway.ioc.lifecycle.ServiceLifecycle;

/**
 * Allows a service to exist "per thread" (in each thread). Creates a proxy that
 * delegates to a per-thread instance.
 * <p>
 * This scheme ensures that, although the service builder method will be invoked
 * many times over the life of the application, the service decoration process
 * occurs only once. The final calling chain is: Service Proxy --&gt;
 * Interceptor(s) (from Decorators) --&gt; Advise Proxy (from Advisiors) --&gt;
 * PerThread Proxy --&gt; (per thread) instance.
 */
@SuppressWarnings("rawtypes")
public class PerThreadServiceLifecycle implements ServiceLifecycle {

    private final PerThreadManager perthreadManager;

    private final JdkProxyFactory proxyFactory;

    public PerThreadServiceLifecycle(
        @Builtin PerThreadManager perthreadManager,
        @Builtin JdkProxyFactory proxyFactory
    ) {
        this.perthreadManager = perthreadManager;
        this.proxyFactory = proxyFactory;
    }

    /**
     * Returns false; this lifecycle represents a service that will be created many
     * times (by each thread).
     */
    @Override
    public boolean isSingleton() {
        return false;
    }

    @Override
    public boolean requiresProxy() {
        return true;
    }

    @Override
    public Object createService(
        ServiceContext resources,
        ObjectCreator creator
    ) {
        ObjectCreator perThreadCreator = perthreadManager.createValue(creator);

        Class serviceInterface = resources.getServiceInterface();

        return proxyFactory.createProxy(
            serviceInterface,
            perThreadCreator,
            "<PerThread Proxy for " +
                resources.getServiceId() +
                "(" +
                serviceInterface.getName() +
                ")>"
        );
    }
}
