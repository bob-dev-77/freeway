package com.jujin.freeway.ioc.annotations;

import java.lang.annotation.*;

/**
 * Marks a field, parameter, or constructor for injection by the Freeway IoC
 * container.
 * <p>
 * When {@code value} is empty (default), injection is performed by type
 * matching &mdash; equivalent to {@link javax.inject.Inject}.
 * <p>
 * When {@code value} specifies a service id, injection is performed by name
 * &mdash; equivalent to {@code @Inject @Named("serviceId")}.
 * <p>
 * This annotation coexists with JSR-330 {@code @javax.inject.Inject}.
 */
@Target({ ElementType.FIELD, ElementType.PARAMETER, ElementType.CONSTRUCTOR })
@Retention(RetentionPolicy.RUNTIME)
@Documented
@UseWith(AnnotationUseContext.SERVICE)
public @interface Inject {
    /** Service id for named injection. Leave empty for type-based injection. */
    String value() default "";
}
