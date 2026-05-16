package com.jujin.freeway.ioc.internal;

import com.jujin.freeway.ioc.AnnotationAccess;
import com.jujin.freeway.ioc.AnnotationProvider;
import com.jujin.freeway.ioc.internal.util.ReflectionUtils;

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
        return ReflectionUtils.toAnnotationProvider(type);
    }

    @Override
    @SuppressWarnings("rawtypes")
    public AnnotationProvider getMethodAnnotationProvider(
        String methodName,
        Class... parameterTypes
    ) {
        return ReflectionUtils.toAnnotationProvider(
            ReflectionUtils.findMethod(type, methodName, parameterTypes)
        );
    }
}
