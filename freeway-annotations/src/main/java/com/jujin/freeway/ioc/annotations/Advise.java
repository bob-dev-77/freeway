package com.jujin.freeway.ioc.annotations;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * An annotation that may be placed on a advisor method of a module. The
 * annotation may/should be used in combination with marker annotations to
 * disambiguate the service to advise. This annotation was introduced as an
 * alternative to the naming convention for advisor methods.
 *
 */
@Target(METHOD)
@Retention(RUNTIME)
@Documented
public @interface Advise {
    /**
     * Type of the service to advise.
     */
    Class<?> serviceInterface() default Object.class;

    /**
     * Id of the advisor.
     */
    String id() default "";
}
