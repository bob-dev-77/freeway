package com.jujin.freeway.ioc.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Marker annotation used to denote a service that is the primary instance of
 * some common interface. This is often used when a service is a
 * {@linkplain com.jujin.freeway.ioc.advisor.ChainBuilder chain of command} or
 * {@linkplain com.jujin.freeway.ioc.advisor.StrategyBuilder strategy-based}
 * and, therefore, many services will implement the same interface.
 */
@Target({ ElementType.PARAMETER, ElementType.FIELD, ElementType.METHOD, ElementType.TYPE })
@Retention(RUNTIME)
@Documented
@UseWith(AnnotationUseContext.SERVICE)
public @interface Primary {

}
