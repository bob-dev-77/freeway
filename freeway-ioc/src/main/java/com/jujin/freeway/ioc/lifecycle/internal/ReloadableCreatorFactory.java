package com.jujin.freeway.ioc.lifecycle.internal;

import com.jujin.freeway.ioc.ServiceBuilderContext;
import com.jujin.freeway.ioc.UpdateListenerHub;
import com.jujin.freeway.ioc.internal.JdkProxyFactory;
import com.jujin.freeway.ioc.lifecycle.ObjectCreator;
import java.lang.reflect.Method;

/**
 * Responsible for creating a
 * {@link ReloadableServiceImplementationObjectCreator} for a service
 * implementation.
 */
@SuppressWarnings("unchecked")
public class ReloadableCreatorFactory implements ObjectCreatorFactory {

    private final JdkProxyFactory proxyFactory;

    private final Method bindMethod;

    private final Class<?> serviceInterfaceClass;

    private final Class<?> serviceImplementationClass;

    private final boolean eagerLoad;

    public ReloadableCreatorFactory(
        JdkProxyFactory proxyFactory,
        Method bindMethod,
        Class<?> serviceInterfaceClass,
        Class<?> serviceImplementationClass,
        boolean eagerLoad
    ) {
        this.proxyFactory = proxyFactory;
        this.bindMethod = bindMethod;
        this.serviceInterfaceClass = serviceInterfaceClass;
        this.serviceImplementationClass = serviceImplementationClass;
        this.eagerLoad = eagerLoad;
    }

    @Override
    public ObjectCreator<?> construct(final ServiceBuilderContext resources) {
        return new ObjectCreator<Object>() {
            @Override
            public Object create() {
                return createReloadableProxy(resources);
            }

            @Override
            public String toString() {
                return bindMethod.getName();
            }
        };
    }

    @Override
    public String description() {
        return String.format(
            "Reloadable %s via %s",
            serviceImplementationClass.getName(),
            bindMethod.getName()
        );
    }

    private Object createReloadableProxy(ServiceBuilderContext resources) {
        ReloadableServiceImplementationObjectCreator reloadableCreator =
            new ReloadableServiceImplementationObjectCreator(
                proxyFactory,
                resources,
                proxyFactory.getClassLoader(),
                serviceImplementationClass.getName()
            );

        resources
            .getService(UpdateListenerHub.class)
            .addUpdateListener(reloadableCreator);

        if (eagerLoad) {
            reloadableCreator.create();
        }

        return proxyFactory.createProxy(
            serviceInterfaceClass,
            reloadableCreator,
            description()
        );
    }
}
