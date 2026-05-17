package com.jujin.freeway.ioc.advisor.internal;

import com.jujin.freeway.ioc.DefaultServiceProxyBuilder;
import com.jujin.freeway.ioc.annotations.Builtin;
import com.jujin.freeway.ioc.internal.JdkProxyFactory;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DefaultServiceProxyBuilderImpl
    implements DefaultServiceProxyBuilder
{

    private final Map<Class<?>, Object> cache = new ConcurrentHashMap<>();

    private final JdkProxyFactory proxyFactory;

    public DefaultServiceProxyBuilderImpl(
        @Builtin JdkProxyFactory proxyFactory
    ) {
        this.proxyFactory = proxyFactory;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <S> S createDefaultImplementation(Class<S> serviceInterface) {
        S instance = (S) cache.get(serviceInterface);

        if (instance == null) {
            instance = createInstance(serviceInterface);
            cache.put(serviceInterface, instance);
        }

        return instance;
    }

    @SuppressWarnings("unchecked")
    private <S> S createInstance(final Class<S> serviceInterface) {
        return (S) Proxy.newProxyInstance(
            proxyFactory.getClassLoader(),
            new Class[] { serviceInterface },
            new DefaultImplementationHandler(serviceInterface)
        );
    }

    private static class DefaultImplementationHandler
        implements InvocationHandler
    {

        private final Class<?> serviceInterface;

        DefaultImplementationHandler(Class<?> serviceInterface) {
            this.serviceInterface = serviceInterface;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args)
            throws Throwable {
            if (method.getDeclaringClass() == Object.class) {
                return switch (method.getName()) {
                    case "toString" -> String.format(
                        "<NoOp %s>",
                        serviceInterface.getName()
                    );
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> method.invoke(this, args);
                };
            }

            Class<?> returnType = method.getReturnType();

            if (returnType == void.class) return null;
            if (returnType == boolean.class) return false;
            if (returnType == byte.class) return (byte) 0;
            if (returnType == short.class) return (short) 0;
            if (returnType == int.class) return 0;
            if (returnType == long.class) return 0L;
            if (returnType == float.class) return 0f;
            if (returnType == double.class) return 0d;
            if (returnType == char.class) return (char) 0;

            return null;
        }
    }
}
