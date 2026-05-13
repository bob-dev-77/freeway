package com.jujin.freeway.ioc.internal;

import com.jujin.freeway.ioc.lifecycle.ObjectCreator;
import com.jujin.freeway.ioc.ServiceLocator;
import com.jujin.freeway.ioc.advisor.OperationTracker;
import com.jujin.freeway.ioc.UpdateListener;
import org.slf4j.Logger;

/**
 * Reloadable object creator for non-service objects.
 */
public class ReloadableObjectCreator
    implements ObjectCreator<Object>, UpdateListener {

    private final ClassReloader reloader;

    public ReloadableObjectCreator(
        JdkProxyFactory proxyFactory,
        ClassLoader baseClassLoader,
        String implementationClassName,
        Logger logger,
        OperationTracker tracker,
        ServiceLocator locator) {
        this.reloader = new ClassReloader(
            clazz -> locator.autobuild(clazz),
            proxyFactory,
            baseClassLoader,
            implementationClassName,
            logger,
            tracker);
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
