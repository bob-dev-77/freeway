package com.jujin.freeway.ioc.internal;

import java.util.ArrayList;
import java.util.List;

/**
 * An {@link IdMatcher} that matches when any of its constituent matchers match
 * (logical OR).
 */
public class OrIdMatcher implements IdMatcher {

    private final List<IdMatcher> matchers;

    public OrIdMatcher(List<IdMatcher> matchers) {
        this.matchers = new ArrayList<>(matchers);
    }

    @Override
    public boolean matches(String id) {
        for (IdMatcher matcher : matchers) {
            if (matcher.matches(id))
                return true;
        }
        return false;
    }
}
