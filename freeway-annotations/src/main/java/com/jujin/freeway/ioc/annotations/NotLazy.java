package com.jujin.freeway.ioc.annotations;

import java.lang.annotation.*;

/**
 * Marks a method as specifically not-lazy, even if other methods in the same
 * interface are being {@linkplain com.jujin.freeway.ioc.advisor.LazyAdvisor
 * advised as lazy}.
 *
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@UseWith(AnnotationUseContext.SERVICE)
public @interface NotLazy {
}
