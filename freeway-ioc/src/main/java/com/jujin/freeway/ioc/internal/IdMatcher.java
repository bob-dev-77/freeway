package com.jujin.freeway.ioc.internal;

/**
 * A matcher of <em>fully qualified</em> ids.
 */
@FunctionalInterface
public interface IdMatcher {
    /**
     * Returns true if the provided input id matches the pattern defined by this
     * matcher instance.
     *
     * @param id
     *            the fully qualified id
     * @return true on match, false otherwise
     */
    boolean matches(String id);
}
