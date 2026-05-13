package com.jujin.freeway.ioc.internal;

import com.jujin.freeway.ioc.AnnotationProvider;

import java.lang.annotation.Annotation;

/**
 * A null implementation of {@link AnnotationProvider}, used when there is not
 * appropriate source of annotations.
 */
public class NullAnnotationProvider implements AnnotationProvider {
    /**
     * Always returns null.
     */
    @Override
    public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
        return null;
    }

}
