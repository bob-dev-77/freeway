package com.jujin.freeway.ioc.internal;

import com.jujin.freeway.ioc.ServiceBuilderContext;
import com.jujin.freeway.ioc.UpdateListener;
import com.jujin.freeway.ioc.internal.util.ReflectionSupport;
import com.jujin.freeway.ioc.lifecycle.ObjectCreator;
import java.lang.reflect.Constructor;

/**
 * Returns an {@link ObjectCreator} for lazily instantiating a given
 * implementation class (with dependencies). Once an instance is instantiated,
 * it is cached ... until any underlying .class file changes, at which point the
 * class (and its class dependencies, such as base classes) are reloaded and a
 * new instance instantiated.
 */
@SuppressWarnings("rawtypes")
public class ReloadableServiceImplementationObjectCreator
    implements ObjectCreator, UpdateListener
{

    private final ClassReloader reloader;

    public ReloadableServiceImplementationObjectCreator(
        JdkProxyFactory proxyFactory,
        ServiceBuilderContext resources,
        ClassLoader baseClassLoader,
        String implementationClassName
    ) {
        this.reloader = new ClassReloader(
            clazz -> {
                final Constructor constructor =
                    ReflectionSupport.findAutobuildConstructor(clazz);

                if (constructor == null) throw new RuntimeException(
                    String.format(
                        "Service implementation class %s does not have a suitable public constructor.",
                        clazz.getName()
                    )
                );

                ObjectCreator constructorServiceCreator =
                    new ConstructorServiceCreator(
                        resources,
                        constructor.toString(),
                        constructor
                    );

                return constructorServiceCreator.create();
            },
            proxyFactory,
            baseClassLoader,
            implementationClassName,
            resources.getLogger(),
            resources.getTracker()
        );
    }

    @Override
    public Object create() {
        return reloader.create();
    }

    @Override
    public void checkForUpdates() {
        reloader.checkForUpdates();
    }
}
