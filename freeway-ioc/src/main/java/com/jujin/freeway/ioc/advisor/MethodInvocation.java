package com.jujin.freeway.ioc.advisor;

import java.lang.reflect.Method;

/**
 * Provides information about a method invocation, and controls how it is
 * handled. Replaces {@code com.jujin.freeway.plastic.MethodInvocation}.
 *
 * @see MethodAdvice
 */
public interface MethodInvocation {
    /**
     * Returns the method being invoked.
     */
    Method getMethod();

    /**
     * Returns a parameter value passed to the method.
     *
     * @param index
     *            the index of the parameter (0-based)
     */
    Object getParameter(int index);

    /**
     * Overrides the value of a parameter.
     */
    void setParameter(int index, Object value);

    /**
     * Returns the return value (only valid after {@link #proceed()} returns and the
     * method has a non-void return type).
     */
    Object getReturnValue();

    /**
     * Sets the return value to be returned to the caller.
     */
    void setReturnValue(Object value);

    /**
     * Proceeds to the next advice in the chain, or to the actual method
     * implementation if there are no more advices.
     */
    void proceed();

    /**
     * Returns true if the method threw a checked exception.
     */
    boolean didThrowCheckedException();

    /**
     * Returns the checked exception thrown by the method.
     *
     * @param <T>
     *            the type of the checked exception
     */
    <T extends Exception> T getCheckedException(Class<T> type);
}
