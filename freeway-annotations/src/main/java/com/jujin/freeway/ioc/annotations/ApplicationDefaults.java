package com.jujin.freeway.ioc.annotations;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Used to disambiguate which version of SymbolProvider is being
 * referenced. Contributions to the ApplicationDefaults symbol source are
 * overridden by JVM System properties.
 *
 * @see FactoryDefaults
 */
@Target({ PARAMETER, FIELD, METHOD })
@Retention(RUNTIME)
@Documented
public @interface ApplicationDefaults {

}
