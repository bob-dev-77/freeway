package com.jujin.freeway.ioc.internal.util;

import com.jujin.freeway.ioc.internal.util.InjectionPlanner;
import com.jujin.freeway.ioc.lifecycle.ObjectCreator;
import java.lang.invoke.MethodHandle;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.function.Supplier;

/**
 * Wraps the invocation of a method as an {@link Invokable}, using JDK 25
 * {@link MethodHandle} instead of reflective {@code method.invoke()}.
 */
public class MethodHandleInvoker<T> implements Supplier<T> {

    private final Object instance;
    private final MethodHandle methodHandle;
    private final boolean isStatic;

    private final ObjectCreator<?>[] methodParameters;

    public MethodHandleInvoker(
        Object instance,
        Method method,
        ObjectCreator<?>[] methodParameters
    ) {
        this.instance = instance;
        this.methodHandle = MethodHandleUtils.methodHandle(method);
        this.isStatic = Modifier.isStatic(method.getModifiers());
        this.methodParameters = methodParameters;
    }

    @Override
    @SuppressWarnings("unchecked")
    public T get() {
        Object[] realized = InjectionPlanner.realizeAll(methodParameters);

        Object[] args;
        if (isStatic) {
            args = realized;
        } else {
            args = new Object[realized.length + 1];
            args[0] = instance;
            System.arraycopy(realized, 0, args, 1, realized.length);
        }

        try {
            return (T) methodHandle.invokeWithArguments(args);
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable t) {
            throw new RuntimeException(
                String.format(
                    "Error invoking method via MethodHandle: %s",
                    InternalUtils.toMessage(t)
                ),
                t
            );
        }
    }
}
