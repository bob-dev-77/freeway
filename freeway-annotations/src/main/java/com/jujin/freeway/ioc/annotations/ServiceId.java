package com.jujin.freeway.ioc.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * An optional annotation that may be placed on a service building method of a
 * module, or on the implementation class (when using service binding via the
 * {@link com.jujin.freeway.ioc.ServiceBinder}). The annotation overrides the
 * default id for services (the default service id is the simple name of the
 * service interface).
 */
@Target({ TYPE, METHOD })
@Retention(RUNTIME)
@Documented
@UseWith({ AnnotationUseContext.SERVICE, AnnotationUseContext.MODULE })
public @interface ServiceId {
    /**
     * An identifier of a service.
     */
    String value();
}
