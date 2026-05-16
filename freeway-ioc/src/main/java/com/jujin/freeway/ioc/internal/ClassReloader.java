package com.jujin.freeway.ioc.internal;

import com.jujin.freeway.ioc.UpdateListener;
import com.jujin.freeway.ioc.advisor.OperationTracker;
import com.jujin.freeway.ioc.internal.util.ExceptionSupport;
import com.jujin.freeway.ioc.internal.util.URLChangeTracker;
import com.jujin.freeway.ioc.lifecycle.ObjectCreator;
import com.jujin.freeway.ioc.lifecycle.ReloadAware;
import java.net.URL;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;
import org.slf4j.Logger;

/**
 * Simplified reloadable object creator that uses standard Java reflection
 * instead of Plastic bytecode manipulation. Supports class reloading by
 * monitoring class file changes and using a fresh class loader.
 */
@SuppressWarnings("rawtypes")
public final class ClassReloader implements ObjectCreator, UpdateListener {

    private final ClassLoader baseClassLoader;

    private final String implementationClassName;

    private final Logger logger;

    private final OperationTracker tracker;

    private final URLChangeTracker changeTracker = new URLChangeTracker();

    private final JdkProxyFactory proxyFactory;

    private final Function<Class<?>, Object> instanceFactory;

    /**
     * The set of class names that should be loaded by the class loader.
     */
    private final Set<String> classesToLoad = new HashSet<>();

    private Object instance;

    private boolean firstTime = true;

    private volatile ClassLoader activeLoader;

    public ClassReloader(
        Function<Class<?>, Object> instanceFactory,
        JdkProxyFactory proxyFactory,
        ClassLoader baseClassLoader,
        String implementationClassName,
        Logger logger,
        OperationTracker tracker
    ) {
        this.instanceFactory = instanceFactory;
        this.proxyFactory = proxyFactory;
        this.baseClassLoader = baseClassLoader;
        this.implementationClassName = implementationClassName;
        this.logger = logger;
        this.tracker = tracker;
    }

    @Override
    public synchronized void checkForUpdates() {
        if (instance == null || !changeTracker.containsChanges()) {
            return;
        }

        logger.debug(
            "Implementation class {} has changed and will be reloaded on next use.",
            implementationClassName
        );

        changeTracker.clear();

        activeLoader = null;

        proxyFactory.clearCache();

        boolean reloadNow = informInstanceOfReload();

        instance = reloadNow ? createInstance() : null;
    }

    private boolean informInstanceOfReload() {
        if (instance instanceof ReloadAware) {
            ReloadAware ra = (ReloadAware) instance;

            return ra.shutdownImplementationForReload();
        }

        return false;
    }

    @Override
    public synchronized Object create() {
        if (instance == null) {
            instance = createInstance();
        }

        return instance;
    }

    private Object createInstance() {
        return tracker.invoke(
            String.format("Reloading class %s.", implementationClassName),
            () -> {
                Class reloadedClass = reloadImplementationClass();

                return instanceFactory.apply(reloadedClass);
            }
        );
    }

    private Class reloadImplementationClass() {
        if (logger.isDebugEnabled()) {
            logger.debug(
                "{} class {}.",
                firstTime ? "Loading" : "Reloading",
                implementationClassName
            );
        }

        try {
            // Use a new child class loader for each reload cycle to support redefinition
            ClassLoader loader = createReloadClassLoader();

            Class result = loader.loadClass(implementationClassName);

            firstTime = false;

            return result;
        } catch (Throwable ex) {
            throw new RuntimeException(
                String.format(
                    "Unable to %s class %s: %s",
                    firstTime ? "load" : "reload",
                    implementationClassName,
                    ExceptionSupport.toMessage(ex)
                ),
                ex
            );
        }
    }

    // TODO 创建热加载的 class loader
    private ClassLoader createReloadClassLoader() {
        ClassLoader parent = baseClassLoader;

        // Reuse the active loader if it still exists, otherwise create a new one
        if (activeLoader != null) {
            return activeLoader;
        }

        activeLoader = parent;

        return activeLoader;
    }

    /**
     * Loads a class via the active reload class loader and tracks its file changes.
     */
    public Class<?> loadAndTransformClass(String className)
        throws ClassNotFoundException {
        logger.debug("BEGIN Loading {}", className);

        ClassLoader loader =
            activeLoader != null ? activeLoader : baseClassLoader;

        Class<?> result = loader.loadClass(className);

        trackClassFileChanges(className);

        logger.debug("  END Loading {}", className);

        return result;
    }

    private void trackClassFileChanges(String className) {
        if (isInnerClassName(className)) {
            return;
        }

        String path = toClassPath(className);

        URL url = baseClassLoader.getResource(path);

        if (isFileURL(url)) {
            changeTracker.add(url, className);
        }
    }

    private boolean isFileURL(URL url) {
        return url != null && url.getProtocol().equals("file");
    }

    private boolean isInnerClassName(String className) {
        return className.indexOf('$') >= 0;
    }

    private static String toClassPath(String className) {
        return className.replace('.', '/') + ".class";
    }
}
