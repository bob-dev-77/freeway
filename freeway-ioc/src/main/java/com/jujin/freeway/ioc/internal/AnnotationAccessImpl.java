package com.jujin.freeway.ioc.internal;

import com.jujin.freeway.ioc.AnnotationAccess;
import com.jujin.freeway.ioc.AnnotationProvider;
import com.jujin.freeway.ioc.internal.util.InternalUtils;

/**
 * Standard AnnotationAccess for a specific type.
 *
 */
public class AnnotationAccessImpl implements AnnotationAccess {

    private final Class<?> type;

    public AnnotationAccessImpl(Class<?> type) {
        this.type = type;
    }

    @Override
    public AnnotationProvider getClassAnnotationProvider() {
        return InternalUtils.toAnnotationProvider(type);
    }

    @Override
    @SuppressWarnings("rawtypes")
    public AnnotationProvider getMethodAnnotationProvider(
        String methodName,
        Class... parameterTypes) {
        return InternalUtils.toAnnotationProvider(
            InternalUtils.findMethod(type, methodName, parameterTypes));
    }
}
