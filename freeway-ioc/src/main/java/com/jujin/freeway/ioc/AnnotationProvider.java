package com.jujin.freeway.ioc;

import java.lang.annotation.Annotation;

/**
 * A source of annotations. This interface is used to mask where the annotations
 * come from (for example, from a Method, a Class, or some other source).
 */
public interface AnnotationProvider {
    /**
     * Searches for the specified annotation, returning the matching annotation
     * instance.
     *
     * @param <T>
     * @param annotationClass
     *            used to select the annotation to return
     * @return the annotation, or null if not found
     */
    <T extends Annotation> T getAnnotation(Class<T> annotationClass);
}
