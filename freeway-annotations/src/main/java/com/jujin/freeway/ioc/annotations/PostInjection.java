package com.jujin.freeway.ioc.annotations;

import java.lang.annotation.*;

/**
 * Annotation for methods that should be invoked after injection. This occurs
 * last: after constructor injection and after field injection. It should be
 * placed on a <strong>public method</strong>. Any return value from the method
 * is ignored. The order of invocation for classes with multiple marked methods
 * (including methods inherited from super-classes) is not, at this time,
 * defined.
 * <p>
 * Freeway also honors the {@link javax.annotation.PostConstruct} annotation,
 * and treats it identically to PostInjection. This is both more flexible than
 * PostConstruct (in that methods may have parameters, and multiple methods may
 * be annotated) but also falls short (Freeway will only seek out public
 * methods).
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@UseWith(AnnotationUseContext.SERVICE)
public @interface PostInjection {
}
