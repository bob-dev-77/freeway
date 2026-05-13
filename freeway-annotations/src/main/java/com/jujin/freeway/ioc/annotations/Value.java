package com.jujin.freeway.ioc.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static com.jujin.freeway.ioc.annotations.AnnotationUseContext.*;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Used in conjunction with {@link javax.inject.Inject} to inject a literal
 * value, rather than a service. Symbols in the value are expanded and the
 * resulting string is coerced to the desired type. For IoC, this annotation is
 * only applied to parameters (on service builder methods, and on service
 * constructors); for components, it may also be applied to field.
 *
 * @see com.jujin.freeway.ioc.symbol.SymbolSource
 */
@Target({ PARAMETER, FIELD })
@Retention(RUNTIME)
@Documented
@UseWith({ COMPONENT, MIXIN, PAGE, SERVICE })
public @interface Value {
    /**
     * The value to be coerced and injected.
     */
    String value();
}
