package com.jujin.freeway.ioc.internal;

import com.jujin.freeway.ioc.config.*;
import com.jujin.freeway.ioc.property.*;
import com.jujin.freeway.ioc.threading.*;
import com.jujin.freeway.ioc.classpath.*;
import com.jujin.freeway.ioc.exception.*;
import com.jujin.freeway.ioc.advisor.MethodAdvice;
import com.jujin.freeway.ioc.config.*;
import com.jujin.freeway.ioc.property.*;
import com.jujin.freeway.ioc.threading.*;
import com.jujin.freeway.ioc.classpath.*;
import com.jujin.freeway.ioc.exception.*;
import com.jujin.freeway.ioc.advisor.MethodInvocation;

import java.lang.invoke.MethodHandle;
import java.lang.reflect.Method;
import java.util.List;

/**
 * JDK 25 native {@link MethodInvocation} backed by {@link MethodHandle} instead
 * of reflective {@code method.invoke()}.
 *
 * <p>
 * The delegate call uses {@code MethodHandle.invokeWithArguments()} which is
 * JIT-compilable, unlike reflective invocation.
 * </p>
 */
final class MethodHandleMethodInvocation implements MethodInvocation {

    private final Method method;
    private final Object[] parameters;
    private final MethodHandle delegateHandle;
    private final List<MethodAdvice> advices;
    private int adviceIndex;
    private Object returnValue;
    private boolean proceeded;
    private Exception checkedException;
    private boolean threwCheckedException;

    MethodHandleMethodInvocation(
        Method method,
        Object[] parameters,
        MethodHandle delegateHandle,
        List<MethodAdvice> advices) {
        this.method = method;
        this.parameters = parameters;
        this.delegateHandle = delegateHandle;
        this.advices = advices;
    }

    @Override
    public Method getMethod() {
        return method;
    }

    @Override
    public Object getParameter(int index) {
        return parameters[index];
    }

    @Override
    public void setParameter(int index, Object value) {
        parameters[index] = value;
    }

    @Override
    public Object getReturnValue() {
        return returnValue;
    }

    @Override
    public void setReturnValue(Object value) {
        returnValue = value;
    }

    @Override
    public void proceed() {
        if (adviceIndex < advices.size()) {
            advices.get(adviceIndex++).advise(this);
        } else if (!proceeded) {
            proceeded = true;
            try {
                // parameters is Object[] — the handle expects individual arguments
                // (e.g. (String)Object), so invokeWithArguments() flattens the array
                // into positional parameters, matching the delegate signature exactly.
                returnValue = delegateHandle.invokeWithArguments(parameters);
            } catch (RuntimeException | Error e) {
                throw e;
            } catch (Throwable e) {
                if (e instanceof Exception) {
                    checkedException = (Exception) e;
                    threwCheckedException = true;
                } else {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    @Override
    public boolean didThrowCheckedException() {
        return threwCheckedException;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends Exception> T getCheckedException(Class<T> type) {
        return (T) checkedException;
    }
}
