package com.jujin.freeway.ioc.internal;

import com.jujin.freeway.ioc.coercion.TypeCoercer;

/**
 * A simplified version of {@link TypeCoercer} used to defer the instantiation
 * of the actual TypeCoercer service until necessary.
 *
 */
public interface TypeCoercerProxy {
    /**
     * Returns input cast to targetType if input is an instance of target type,
     * otherwise delegates to {@link TypeCoercer#coerce(Object, Class)}.
     *
     * @param <S>
     * @param <T>
     * @param input
     *            value to be coerced
     * @param targetType
     *            desired type of value
     * @return the value, coerced
     * @throws RuntimeException
     *             if the input can not be coerced
     */
    <S, T> T coerce(S input, Class<T> targetType);

}
