package com.jujin.freeway.ioc.coercion;

import com.jujin.freeway.ioc.exception.UnknownValueException;

import java.util.List;

/**
 * Exception used when {@link TypeCoercer} doesn't find a coercion from a type
 * to another.
 *
 */
public class CoercionNotFoundException extends UnknownValueException {

    private static final long serialVersionUID = 1L;

    private final Class<?> sourceType;

    private final Class<?> targetType;

    public CoercionNotFoundException(
        String message,
        List<String> availableValues,
        Class<?> sourceType,
        Class<?> targetType) {
        super(message, availableValues);
        this.sourceType = sourceType;
        this.targetType = targetType;
    }

    /**
     * Returns the source type.
     */
    public Class<?> getSourceType() {
        return sourceType;
    }

    /**
     * Returns the target type.
     */
    public Class<?> getTargetType() {
        return targetType;
    }
}
