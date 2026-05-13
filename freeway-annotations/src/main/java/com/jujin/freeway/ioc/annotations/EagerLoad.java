package com.jujin.freeway.ioc.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Marker annotation placed on a service builder method to indicate that the
 * service should be eagerly loaded: realized as if a service method had been
 * invoked. Service realization invokes the service builder method and applys
 * any decorators to the service.
 * <p>
 * This annotation may also be placed directly on a service implementation
 * class, when using autobuilding via the
 * {@link com.jujin.freeway.ioc.ServiceBinder}.
 *
 * @see com.jujin.freeway.ioc.ServiceBindingOptions#eagerLoad()
 */
@Target({ TYPE, METHOD })
@Retention(RUNTIME)
@Documented
@UseWith(AnnotationUseContext.SERVICE)
public @interface EagerLoad {

}
