package com.jujin.freeway.ioc.annotations;

import java.lang.annotation.*;

/**
 * A documentation-only interface placed on service interfaces for services
 * which have an {@link com.jujin.freeway.ioc.config.OrderedConfiguration}, to identify
 * the type of contribution.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.CLASS)
@Documented
@UseWith(AnnotationUseContext.SERVICE)
public @interface UsesOrderedConfiguration {
    /**
     * The type of object which may be contributed into the service's configuration.
     */
    Class<?> value();
}
