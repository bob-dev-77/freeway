package com.jujin.freeway.ioc.internal;

import com.jujin.freeway.ioc.annotations.Builtin;
import com.jujin.freeway.ioc.internal.util.StringUtils;
import com.jujin.freeway.ioc.lifecycle.ObjectCreator;
import com.jujin.freeway.ioc.threading.ThunkCreator;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

public class ThunkCreatorImpl implements ThunkCreator {

    private final JdkProxyFactory proxyFactory;

    public ThunkCreatorImpl(@Builtin JdkProxyFactory proxyFactory) {
        this.proxyFactory = proxyFactory;
    }

    @Override
    public <T> T createThunk(
        Class<T> proxyType,
        ObjectCreator objectCreator,
        String description
    ) {
        assert proxyType != null;
        assert objectCreator != null;
        assert StringUtils.isNonBlank(description);

        if (!proxyType.isInterface()) throw new IllegalArgumentException(
            String.format(
                "Thunks may only be created for interfaces; %s is a class.",
                proxyType.getName()
            )
        );

        return proxyType.cast(
            Proxy.newProxyInstance(
                proxyFactory.getClassLoader(),
                new Class[] { proxyType },
                new ThunkInvocationHandler(
                    proxyType,
                    objectCreator,
                    description
                )
            )
        );
    }

    private static class ThunkInvocationHandler<
        T
    > implements InvocationHandler {

        private final Class<T> proxyType;
        private final ObjectCreator<T> objectCreator;
        private final String description;

        ThunkInvocationHandler(
            Class<T> proxyType,
            ObjectCreator<T> objectCreator,
            String description
        ) {
            this.proxyType = proxyType;
            this.objectCreator = objectCreator;
            this.description = description;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args)
            throws Throwable {
            if (method.getDeclaringClass() == Object.class) {
                return switch (method.getName()) {
                    case "toString" -> description;
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> method.invoke(this, args);
                };
            }

            T delegate = objectCreator.create();
            return method.invoke(delegate, args);
        }
    }
}
