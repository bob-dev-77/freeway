package com.jujin.freeway.ioc.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Used to disambiguate which version of SymbolProvider is being
 * referenced. Symbols defined by contributing to FactoryDefaults are overridden
 * by contributions to ApplicationDefaults.
 */
@Target({ PARAMETER, FIELD, METHOD })
@Retention(RUNTIME)
@Documented
public @interface FactoryDefaults {

}
