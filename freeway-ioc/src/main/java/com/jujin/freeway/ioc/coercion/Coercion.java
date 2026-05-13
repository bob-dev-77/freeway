package com.jujin.freeway.ioc.coercion;

/**
 * Responsible for converting from one type to another. This is used primarily
 * around component parameters.
 *
 * @param <S>
 *            the source type (input)
 * @param <T>
 *            the target type (output)
 */
public interface Coercion<S, T> {
    /**
     * Converts an input value.
     *
     * @param input
     *            the input value
     */
    T coerce(S input);
}
