package com.jujin.freeway.ioc.internal.util;

/**
 * Defines standard scope names used for service lifecycles.
 */
public final class Scopes {

    /** The singleton scope — one instance per registry. */
    public static final String SINGLETON = "singleton";

    /** The per-thread scope — one instance per thread. */
    public static final String PERTHREAD = "perthread";

    private Scopes() {}
}
