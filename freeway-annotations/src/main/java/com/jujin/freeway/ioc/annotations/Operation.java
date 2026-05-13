package com.jujin.freeway.ioc.annotations;

import java.lang.annotation.*;

/**
 * Describes a method as one that should be operation tracked. Operation
 * tracking is useful when an exception in deeply nested code occurs, as it is
 * possible to identify (using human readable descriptions) the path to the code
 * that failed.
 *
 * @see com.jujin.freeway.ioc.advisor.OperationTracker
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@UseWith({ AnnotationUseContext.SERVICE, AnnotationUseContext.COMPONENT, AnnotationUseContext.PAGE })
public @interface Operation {
    /**
     * The message to pass to
     * {@link com.jujin.freeway.ioc.advisor.OperationTracker#invoke(String, java.util.function.Supplier)}.
     * If the message contains the '%' character, it is interpreted to be a
     * {@linkplain java.util.Formatter format string}, passed the method's
     * parameters.
     *
     * @see com.jujin.freeway.ioc.advisor.OperationAdvisor#createAdvice(String)
     */
    String value();
}
