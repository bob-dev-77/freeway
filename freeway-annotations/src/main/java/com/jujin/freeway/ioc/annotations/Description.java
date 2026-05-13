package com.jujin.freeway.ioc.annotations;

import java.lang.annotation.Documented;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.ElementType.PACKAGE;

import java.lang.annotation.Retention;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Target;

import static com.jujin.freeway.ioc.annotations.AnnotationUseContext.*;

/**
 * Annotation used by Freeway to describe the annotated class or package in
 * runtime, specially in the T5Dashboard page.
 */
@Target({ TYPE, PACKAGE })
@Retention(RUNTIME)
@Documented
@UseWith({ COMPONENT, MIXIN, PAGE, SERVICE })
public @interface Description {
    /**
     * A textual description of this class.
     */
    String text();

    /**
     * Tags used to describe this class. Use just lowercase letters, numbers and
     * dashes.
     */
    String[] tags() default {};

}
