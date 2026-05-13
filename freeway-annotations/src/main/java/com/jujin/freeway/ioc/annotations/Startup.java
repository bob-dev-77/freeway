package com.jujin.freeway.ioc.annotations;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * An annotation that may be placed on a startup method of a module. A startup
 * method is an simple way to provide extra logic to be executed at
 * {@link com.jujin.freeway.ioc.Registry#performRegistryStartup()}. Instead of
 * making contributions to the <i>RegistryStartup</i> service configuration you
 * can provide startup methods inside your modules.
 *
 */
@Target(METHOD)
@Retention(RUNTIME)
@Documented
public @interface Startup {
}
