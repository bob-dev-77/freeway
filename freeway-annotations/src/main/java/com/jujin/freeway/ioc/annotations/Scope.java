package com.jujin.freeway.ioc.annotations;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * An optional annotation that may be placed on a service building method of a
 * module, or on the implementation class (when using service binding). The
 * annotation overrides the default scope for services (the default being a
 * global singleton that is instantiated on demand) for an alternate lifecycle.
 * Alternate lifecycles are typically used to bind a service implementation to a
 * single thread or request. Modules may define new scopes. Each scope should
 * have a corresponding {@link com.jujin.freeway.ioc.lifecycle.ServiceLifecycle}
 * implementation. The linkage from scope name to service lifecycle occurs via a
 * contribution to the
 * {@link com.jujin.freeway.ioc.lifecycle.ServiceLifecycleSource} service
 * configuration.
 * <p>
 * The annotation may also be placed directly on a service implementation class,
 * when using service binding (via the
 * {@link com.jujin.freeway.ioc.ServiceBinder}).
 *
 * @see com.jujin.freeway.ioc.internal.util.InternalUtils.
 */
@Target({ TYPE, METHOD })
@Retention(RUNTIME)
@Documented
@UseWith({ AnnotationUseContext.SERVICE, AnnotationUseContext.MODULE })
public @interface Scope {
    /**
     * An identifier used to look up a non-default
     * {@link com.jujin.freeway.ioc.lifecycle.ServiceLifecycle}.
     */
    String value();
}
