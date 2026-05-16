package com.jujin.freeway.ioc;

import com.jujin.freeway.ioc.annotations.EagerLoad;
import com.jujin.freeway.ioc.annotations.ImportModule;
import com.jujin.freeway.ioc.internal.*;
import com.jujin.freeway.ioc.internal.util.ExceptionSupport;
import com.jujin.freeway.ioc.internal.util.OneShotLock;
import org.slf4j.Logger;

import java.lang.reflect.AnnotatedElement;
import java.util.*;

/**
 * Public access to the IoC service registry.
 */
public interface Registry extends ServiceLocator {
    /**
     * Invoked at the end of a request to discard any thread-specific information
     * accumulated during the current request.
     *
     * @see com.jujin.freeway.ioc.threading.PerThreadManager
     */
    void cleanupThread();

    /**
     * Shuts down a Registry instance. Notifies all listeners that the registry has
     * shutdown. Further method invocations on the Registry are no longer allowed,
     * and the Registry instance should be discarded.
     *
     * @see com.jujin.freeway.ioc.RegistryShutdownHub
     */
    void shutdown();

    /**
     * Invoked to eagerly load services marked with the {@link EagerLoad}
     * annotation, and to execute all contributions to the Startup service.
     */
    void performRegistryStartup();

    /**
     * Used to construct the IoC {@link Registry}. This class is <em>not</em>
     * thread-safe. The Registry, once created, <em>is</em> thread-safe.
     */
    public static final class Builder {

        private final OneShotLock lock = new OneShotLock();

        /**
         * Module defs, keyed on module id.
         */
        final List<ModuleDefinition> modules = new ArrayList<>();

        private final ClassLoader classLoader;

        private final Logger logger;

        private final LoggerSource loggerSource;

        private final JdkProxyFactory proxyFactory;

        private final Set<Class<?>> addedModuleClasses = new HashSet<>();

        public Builder() {
            this(Thread.currentThread().getContextClassLoader());
        }

        public Builder(ClassLoader classLoader) {
            this(classLoader, new LoggerSourceImpl());
        }

        public Builder(ClassLoader classLoader, LoggerSource loggerSource) {
            this.classLoader = classLoader;
            this.loggerSource = loggerSource;
            logger = loggerSource.getLogger(Builder.class);

            // Make the Proxy Factory appear to be a service inside FreewayIOCModule, even
            // before that
            // module exists.

            proxyFactory = new JdkProxyFactory(this.classLoader);

            add(FreewayIOCModule.class);
        }

        /**
         * Adds a {@link ModuleDefinition} to the registry, returning the builder for further
         * configuration.
         */
        public Builder add(ModuleDefinition moduleDef) {
            lock.check();

            // NOTE: Duplicate module detection is handled in add(Class...) via
            // addedModuleClasses.
            // ModuleDefinition instances have no identity concept, so dedup is best-effort at the
            // class level.

            modules.add(moduleDef);

            return this;
        }

        /**
         * Adds a number of modules (as module classes) to the registry, returning the
         * builder for further configuration.
         *
         * @see com.jujin.freeway.ioc.annotations.ImportModule
         */
        public Builder add(Class<?>... moduleClasses) {
            lock.check();

            var queue = new ArrayList<>(Arrays.asList(moduleClasses));

            while (!queue.isEmpty()) {
                var c = queue.remove(0);

                // Quietly ignore previously added classes.

                if (addedModuleClasses.contains(c))
                    continue;

                addedModuleClasses.add(c);

                logger.info("Adding module definition for " + c);

                ModuleDefinition def = new DefaultModuleDefinition(
                    c,
                    logger,
                    proxyFactory);
                add(def);

                var element = (AnnotatedElement) c;

                var importModule = element.getAnnotation(ImportModule.class);

                if (importModule != null) {
                    for (Class<?> mc : importModule.value()) {
                        queue.add(mc);
                    }
                }
            }

            return this;
        }

        /**
         * Adds a module class (specified by fully qualified class name) to the registry,
         * returning the builder for further configuration.
         *
         * @see com.jujin.freeway.ioc.annotations.ImportModule
         */
        public Builder add(String classname) {
            lock.check();

            try {
                Class<?> builderClass = Class.forName(
                    classname,
                    true,
                    classLoader);

                add(builderClass);
            } catch (Exception ex) {
                throw new RuntimeException(
                    String.format(
                        "Failure loading Freeway IoC module class %s: %s",
                        classname,
                        ExceptionSupport.toMessage(ex)),
                    ex);
            }

            return this;
        }

        /**
         * Auto-discovers module classes on the classpath using JDK
         * {@link ServiceLoader}.
         *
         * <p>
         * Scans for {@code META-INF/services/com.jujin.freeway.ioc.ModuleProvider}
         * files and loads all declared {@link ModuleProvider} implementations. Each
         * provider may declare one or more module classes, all of which are added to
         * the registry via {@link #add(Class...)}.
         * </p>
         *
         * <p>
         * A typical SPI declaration for a library module:
         * </p>
         *
         * <pre>{@code
         * # META-INF/services/com.jujin.freeway.ioc.ModuleProvider
         * com.example.mylib.MyLibModuleProvider
         * }</pre>
         *
         * @return this builder, for method chaining
         */
        public Builder autoDiscover() {
            lock.check();

            var loader = ServiceLoader.load(ModuleProvider.class, classLoader);

            var discovered = new ArrayList<Class<?>>();
            for (ModuleProvider provider : loader) {
                Class<?>[] moduleClasses = provider.modules();
                if (moduleClasses == null)
                    continue;
                for (Class<?> moduleClass : moduleClasses) {
                    if (moduleClass != null &&
                        !addedModuleClasses.contains(moduleClass)) {
                        discovered.add(moduleClass);
                    }
                }
            }

            if (!discovered.isEmpty()) {
                logger.info(
                    "Auto-discovered {} module(s) via SPI: {}",
                    discovered.size(),
                    discovered);
                add(discovered.toArray(new Class<?>[0]));
            } else {
                logger.debug(
                    "No modules discovered via SPI (no ModuleProvider implementations found)");
            }

            return this;
        }

        /**
         * Constructs and returns the registry; this may only be done once. The caller
         * is responsible for invoking
         * {@link com.jujin.freeway.ioc.Registry#performRegistryStartup()}.
         */
        public Registry build() {
            lock.lock();

            var tracker = new PerThreadOperationTracker(
                loggerSource.getLogger(Registry.class));

            var registry = new RegistryImpl(
                modules,
                proxyFactory,
                loggerSource,
                tracker);

            return registry;
        }

        public ClassLoader getClassLoader() {
            return classLoader;
        }

        public Logger getLogger() {
            return logger;
        }

        /**
         * Constructs the registry, adds a {@link ModuleDefinition} and a number of modules (as
         * module classes) to the registry and performs registry startup. The returned
         * registry is ready to use. The caller is must not invoke
         * {@link com.jujin.freeway.ioc.Registry#performRegistryStartup()}.
         *
         * @param moduleDef
         *            {@link ModuleDefinition} to add
         * @param moduleClasses
         *            modules (as module classes) to add
         * @return {@link Registry}
         */
        public static Registry startAndBuild(
            ModuleDefinition moduleDef,
            Class<?>... moduleClasses) {
            var builder = new Builder();

            if (moduleDef != null)
                builder.add(moduleDef);

            builder.add(moduleClasses);

            var registry = builder.build();

            registry.performRegistryStartup();

            return registry;
        }

        /**
         * Constructs the registry, adds a number of modules (as module classes) to the
         * registry and performs registry startup. The returned registry is ready to
         * use. The caller is must not invoke
         * {@link com.jujin.freeway.ioc.Registry#performRegistryStartup()}.
         *
         * @param moduleClasses
         *            modules (as module classes) to add
         * @return {@link Registry}
         */
        public static Registry startAndBuild(Class<?>... moduleClasses) {
            return startAndBuild(null, moduleClasses);
        }

        /**
         * Constructs the registry with auto-discovered modules (via JDK SPI) plus any
         * explicitly provided module classes, then performs registry startup.
         *
         * <p>
         * This is the preferred way to build a registry in JDK 25 applications that
         * want full classpath scanning for IoC modules.
         * </p>
         *
         * @param moduleClasses
         *            additional module classes to include (may be empty)
         * @return a fully started {@link Registry}
         */
        public static Registry spiAndBuild(Class<?>... moduleClasses) {
            var builder = new Builder();

            builder.autoDiscover();

            if (moduleClasses.length > 0) {
                builder.add(moduleClasses);
            }

            var registry = builder.build();
            registry.performRegistryStartup();

            return registry;
        }
    }
}
