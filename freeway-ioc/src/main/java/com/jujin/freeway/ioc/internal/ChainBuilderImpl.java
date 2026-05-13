package com.jujin.freeway.ioc.internal;

import com.jujin.freeway.ioc.advisor.ChainBuilder;
import com.jujin.freeway.ioc.annotations.Builtin;

import java.lang.reflect.Array;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;

public class ChainBuilderImpl implements ChainBuilder {

    private final JdkProxyFactory proxyFactory;

    public ChainBuilderImpl(@Builtin JdkProxyFactory proxyFactory) {
        this.proxyFactory = proxyFactory;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T build(final Class<T> commandInterface, List<T> commands) {
        Object[] array = (Object[]) Array.newInstance(
            commandInterface,
            commands.size());

        final Object[] commandsArray = commands.toArray(array);

        return (T) Proxy.newProxyInstance(
            proxyFactory.getClassLoader(),
            new Class[]{ commandInterface },
            new ChainInvocationHandler(commandInterface, commandsArray));
    }

    private static class ChainInvocationHandler implements InvocationHandler {

        private final Class<?> commandInterface;
        private final Object[] commandsArray;

        ChainInvocationHandler(
            Class<?> commandInterface,
            Object[] commandsArray) {
            this.commandInterface = commandInterface;
            this.commandsArray = commandsArray;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args)
            throws Throwable {
            if (method.getDeclaringClass() == Object.class) {
                return switch (method.getName()) {
                    case "toString" -> String.format(
                        "<Command chain of %s>",
                        commandInterface.getName());
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> method.invoke(this, args);
                };
            }

            Object result = null;
            boolean hasResult = false;

            for (Object command : commandsArray) {
                Object value = method.invoke(command, args);

                if (method.getReturnType() == void.class) {
                    continue;
                }

                if (value == null) {
                    continue;
                }

                if (method.getReturnType() == boolean.class) {
                    if ((Boolean) value) {
                        return true;
                    }
                    continue;
                }

                if (method.getReturnType() == int.class ||
                    method.getReturnType() == long.class ||
                    method.getReturnType() == double.class ||
                    method.getReturnType() == float.class) {
                    Number num = (Number) value;
                    if (num.doubleValue() != 0) {
                        return value;
                    }
                    continue;
                }

                // For primitive char, short, byte
                if (method.getReturnType() == char.class) {
                    if ((Character) value != 0) {
                        return value;
                    }
                    continue;
                }

                // For non-null object return
                result = value;
                hasResult = true;
            }

            if (hasResult)
                return result;

            // Return default value
            Class<?> returnType = method.getReturnType();
            if (returnType == void.class)
                return null;
            if (returnType == boolean.class)
                return false;
            if (returnType == byte.class)
                return (byte) 0;
            if (returnType == short.class)
                return (short) 0;
            if (returnType == int.class)
                return 0;
            if (returnType == long.class)
                return 0L;
            if (returnType == float.class)
                return 0f;
            if (returnType == double.class)
                return 0d;
            if (returnType == char.class)
                return (char) 0;

            return null;
        }
    }
}
