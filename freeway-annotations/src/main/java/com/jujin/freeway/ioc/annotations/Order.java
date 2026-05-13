package com.jujin.freeway.ioc.annotations;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Used with a service decorator method to control the order in which
 * decorations occur. Identifies other decorators which should occur before the
 * annotated decorator.
 *
 * @see com.jujin.freeway.ioc.advisor.DecoratorDef
 */
@Target(METHOD)
@Retention(RUNTIME)
@Documented
@UseWith(AnnotationUseContext.SERVICE_DECORATOR)
public @interface Order {
    /**
     * Any number of ordering constraint strings.
     */
    String[] value();
}
