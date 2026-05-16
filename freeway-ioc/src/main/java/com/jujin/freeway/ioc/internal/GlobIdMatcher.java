package com.jujin.freeway.ioc.internal;

import com.jujin.freeway.ioc.internal.util.GlobPatternMatcher;

/**
 * An {@link IdMatcher} that matches a service ID using a glob-style pattern.
 */
public class GlobIdMatcher implements IdMatcher {

    private final GlobPatternMatcher matcher;

    public GlobIdMatcher(String pattern) {
        this.matcher = new GlobPatternMatcher(pattern);
    }

    @Override
    public boolean matches(String id) {
        return matcher.matches(id);
    }
}
