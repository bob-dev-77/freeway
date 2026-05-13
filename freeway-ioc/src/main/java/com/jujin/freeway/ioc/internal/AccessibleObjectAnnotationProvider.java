package com.jujin.freeway.ioc.internal;

import com.jujin.freeway.ioc.AnnotationProvider;

import java.lang.annotation.Annotation;
import java.lang.reflect.AccessibleObject;

/**
 * Provides access to annotations of an accessible object such as a
 * {@link java.lang.reflect.Method} or {@link java.lang.reflect.Field}.
 */
public class AccessibleObjectAnnotationProvider implements AnnotationProvider {

    private final AccessibleObject object;

    public AccessibleObjectAnnotationProvider(AccessibleObject object) {
        this.object = object;
    }

    @Override
    public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
        return object.getAnnotation(annotationClass);
    }

    @Override
    public String toString() {
        return String.format("AnnotationProvider[%s]", object);
    }
}
