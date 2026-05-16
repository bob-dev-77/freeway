package com.jujin.freeway.ioc.internal.util;

/**
 * Central constants for the freeway-ioc module.
 */
public final class IocConstants {

    /** Service ID for the dependency resolver (ObjectInjector). */
    public static final String RESOLVER_SERVICE_ID = "DependencyResolver";

    /** Name of a JVM System Property used to disable live service reloading. */
    public static final boolean SERVICE_CLASS_RELOADING_ENABLED =
        Boolean.parseBoolean(System.getProperty("freeway.service-reloading-enabled", "true"));

    /** Manifest entry name used to identify Freeway module classes. */
    public static final String MODULE_BUILDER_MANIFEST_ENTRY_NAME = "Freeway-Module-Classes";

    /** Configuration symbol for the thread pool core size. */
    public static final String THREAD_POOL_CORE_SIZE = "freeway.thread-pool.core-pool-size";

    /** Configuration symbol for the thread pool max size. */
    public static final String THREAD_POOL_MAX_SIZE = "freeway.thread-pool.max-pool-size";

    /** Configuration symbol for the thread pool keep-alive time. */
    public static final String THREAD_POOL_KEEP_ALIVE = "freeway.thread-pool.keep-alive";

    /** Configuration symbol for whether the thread pool is enabled. */
    public static final String THREAD_POOL_ENABLED = "freeway.thread-pool-enabled";

    /** Configuration symbol for the thread pool queue size. */
    public static final String THREAD_POOL_QUEUE_SIZE = "freeway.thread-pool.queue-size";

    /** Configuration symbol for the proxy mechanism. */
    public static final String PROXY_MECHANISM = "freeway.proxy-mechanism";

    private IocConstants() {}
}
