package com.jujin.freeway.ioc.annotations;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Attached to a module class, this annotation identifies other module classes
 * that should also be added to the Registry. A class is processed once, even if
 * it is mentioned multiple times. Using this annotation is often easier than
 * updating the JAR Manifest to list additional module class names.
 *
 */
@Target(TYPE)
@Retention(RUNTIME)
@Documented
@UseWith(AnnotationUseContext.MODULE)
public @interface ImportModule {
    /**
     * One or more classes that are also modules and should also be loaded.
     */
    Class<?>[] value();
}
