package com.jujin.freeway.ioc.annotations;

import java.lang.annotation.*;

/**
 * A special marker annotation which limits the search for possible services to
 * just the <em>same</em> module containing the service being injected. Other
 * marker annotations may also be applied. It is allowed on methods to support
 * the @Contribute annotation (used as a preferred alternative to the older
 * naming convention for identifying contribute methods and targetted services).
 */
@Target({ ElementType.PARAMETER, ElementType.FIELD, ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
@Documented
@UseWith(AnnotationUseContext.SERVICE)
public @interface Local {
}
