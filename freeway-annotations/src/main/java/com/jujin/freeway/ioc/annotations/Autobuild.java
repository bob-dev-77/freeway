package com.jujin.freeway.ioc.annotations;

import java.lang.annotation.*;

/**
 * Directs that the value to be built should be an autobuild instance of the
 * type with injections performed, via
 * {@link com.jujin.freeway.ioc.ServiceLocator#autobuild(Class)}. This should
 * only be placed on a field or parameter of an instantiable type (not an
 * interface).
 *
 */
@Target({ ElementType.PARAMETER, ElementType.FIELD })
@Retention(RetentionPolicy.RUNTIME)
@Documented
@UseWith(AnnotationUseContext.SERVICE)
public @interface Autobuild {
}
