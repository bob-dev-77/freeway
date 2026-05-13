package com.jujin.freeway.ioc.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * An annotation that may be placed on a contributor method of a module. The
 * annotation may/should be used in combination with {@link Marker} annotation
 * to disambiguate the service to contribute into. This annotation was
 * introduced as an alternative to the naming convention for contributor
 * methods.
 *
 * @see Optional
 */
@Target(METHOD)
@Retention(RUNTIME)
@Documented
public @interface Contribute {
    /**
     * Type of the service to contribute into.
     */
    Class<?> value();
}
