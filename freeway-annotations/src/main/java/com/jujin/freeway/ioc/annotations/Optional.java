package com.jujin.freeway.ioc.annotations;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Marks a service contribution method within a module as being optional: it is
 * not an error if the contribution does not match against an actual service. In
 * that case, the method will simply never be invoked. This is occasionally
 * useful when a module is designed to work with another module <em>if the
 * second module is present</em>. Without optional contributions, you would see
 * hard errors when registry is created, and have to create a layer cake of
 * small modules to prevent such errors.
 *
 * @see Contribute
 * @see com.jujin.freeway.ioc.ContributionDef#isOptional()
 */
@Target(METHOD)
@Retention(RUNTIME)
@Documented
@UseWith(AnnotationUseContext.MODULE)
public @interface Optional {
}
