package com.jujin.freeway.ioc.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static com.jujin.freeway.ioc.annotations.AnnotationUseContext.*;

/**
 * Used to inject a symbol value, via a symbol name. This is used much like
 * {@link com.jujin.freeway.ioc.annotations.Value} annotation, except that
 * symbols are not expanded ... the entire value is a symbol name. This allows
 * the annotation to reference a public constant variable.
 * <p>
 * <p>
 * The injected value may be coerced from string to an alternate type (defined
 * by the field or parameter to which the @Symbol annotation is attached). For
 * better control, use the {@link IntermediateType} annotation as well, which
 * allows the string to be coerced to an alternate type before being coerced a
 * second time to the field or parameter type.
 */
@Target({ PARAMETER, FIELD })
@Retention(RUNTIME)
@Documented
@UseWith({SERVICE })
public @interface Symbol {
    /**
     * The name of the symbol to inject.
     */
    String value();
}
