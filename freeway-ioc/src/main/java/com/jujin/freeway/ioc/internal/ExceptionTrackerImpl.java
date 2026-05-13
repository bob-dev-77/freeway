package com.jujin.freeway.ioc.internal;

import com.jujin.freeway.ioc.annotations.PreventServiceDecoration;
import com.jujin.freeway.ioc.annotations.Scope;
import com.jujin.freeway.ioc.exception.ExceptionTracker;
import com.jujin.freeway.ioc.internal.util.InternalUtils;

import java.util.HashSet;
import java.util.Set;

@Scope(InternalUtils.PERTHREAD)
@PreventServiceDecoration
public class ExceptionTrackerImpl implements ExceptionTracker {
    private final Set<Throwable> exceptions = new HashSet<>();

    @Override
    public boolean exceptionLogged(Throwable exception) {
        boolean result = exceptions.contains(exception);

        exceptions.add(exception);

        return result;
    }
}
