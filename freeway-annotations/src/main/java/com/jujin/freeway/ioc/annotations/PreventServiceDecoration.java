package com.jujin.freeway.ioc.annotations;

import java.lang.annotation.*;

/**
 * Marks a service as not eligible for decoration. This is useful for services
 * that, if decorated, can cause cycle dependency errors; for example,
 * {@link com.jujin.freeway.ioc.ObjectInjector}, or services <em>contributed
 * to</em> ObjectInjector, are good candidates for this annotation.
 * <p>
 * The annotation can be applied to service implementation class or to a service
 * builder method in a module class.
 * <p>
 * The annotation may also be placed on a module class, to indicate that all
 * services defined for the module should not allow decoration.
 * <p>
 * Service decoration includes the decoration mechanism (from Freeway 5.0) and
 * the newer service advice mechanism (from Freeway 5.1).
 * <p>
 * Generally, services that are used to advise or decorate other services (such
 * as {@link com.jujin.freeway.ioc.advisor.LoggingAdvisor} or
 * {@link com.jujin.freeway.ioc.advisor.OperationAdvisor}) should include this
 * annotation, to prevent a recursive service build when they attempt to advise
 * themselves.
 *
 * @see com.jujin.freeway.ioc.ServiceDefinition#isPreventDecoration()
 */
@Target({ ElementType.TYPE, ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
@Documented
@UseWith(AnnotationUseContext.SERVICE)
public @interface PreventServiceDecoration {
}
