package com.jujin.freeway.ioc.annotations;

import java.lang.annotation.*;

/**
 * Used to guide Freeway when coercing from a raw type to a field or parameter
 * type, by forcing Freeway to coerce to the intermediate type. This was
 * introduced to allow coercion from string to a time period (in milliseconds)
 * via {@link com.jujin.freeway.ioc.schedule.TimeInterval}
 *
 * @see com.jujin.freeway.ioc.annotations.Value
 * @see com.jujin.freeway.ioc.annotations.Symbol
 */
@Target({ ElementType.PARAMETER, ElementType.FIELD })
@Retention(RetentionPolicy.RUNTIME)
@Documented
@UseWith(AnnotationUseContext.SERVICE)
public @interface IntermediateType {
    /**
     * The intermediate to coerce through.
     */
    Class<?> value();
}
