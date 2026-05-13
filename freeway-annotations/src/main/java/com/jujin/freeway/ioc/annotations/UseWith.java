package com.jujin.freeway.ioc.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.RetentionPolicy.SOURCE;

/**
 * Annotation documenting the context(s) in which freeway-provided annotations
 * may be used. This annotation is solely for documentation purposes, is
 * expressly not used at runtime
 *
 */
@Target(ElementType.ANNOTATION_TYPE)
@Retention(SOURCE)
@Documented
public @interface UseWith {
    AnnotationUseContext[] value();
}
