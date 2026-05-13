package com.jujin.freeway.ioc.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A documentation-only interface placed on service interfaces for services
 * which have a {@link com.jujin.freeway.ioc.MappedConfiguration}, to identify
 * the type of key (often, a String), and type of contribution.
 * <p>
 * Remember that when the key type is String, the map will be case-insensitive.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.CLASS)
@Documented
@UseWith(AnnotationUseContext.SERVICE)
public @interface UsesMappedConfiguration {
    /**
     * The type of key used to identify contribution values.
     */
    Class<?> key() default String.class;

    /**
     * The type of object which may be contributed into the service's configuration.
     */
    Class<?> value();
}
