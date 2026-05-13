package com.jujin.freeway.ioc.internal;

import com.jujin.freeway.ioc.coercion.Coercion;

/**
 * Combines two coercions to create a coercion through an intermediate type.
 *
 * @param <S>
 *            The source (input) type
 * @param <I>
 *            The intermediate type
 * @param <T>
 *            The target (output) type
 */
public class CompoundCoercion<S, I, T> implements Coercion<S, T> {
    private final Coercion<S, I> op1;

    private final Coercion<I, T> op2;

    public CompoundCoercion(Coercion<S, I> op1, Coercion<I, T> op2) {
        this.op1 = op1;
        this.op2 = op2;
    }

    @Override
    public T coerce(S input) {
        // Run the input through the first operation (S --> I), then run the result of
        // that through
        // the second operation (I --> T).

        I intermediate = op1.coerce(input);

        return op2.coerce(intermediate);
    }

    @Override
    public String toString() {
        return String.format("%s, %s", op1, op2);
    }
}
