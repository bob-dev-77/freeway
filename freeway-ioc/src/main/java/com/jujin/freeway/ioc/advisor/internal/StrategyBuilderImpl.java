package com.jujin.freeway.ioc.advisor.internal;

import com.jujin.freeway.ioc.advisor.StrategyBuilder;
import com.jujin.freeway.ioc.advisor.StrategyRegistry;
import com.jujin.freeway.ioc.annotations.Builtin;
import com.jujin.freeway.ioc.internal.JdkProxyFactory;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;

public class StrategyBuilderImpl implements StrategyBuilder {

    private final JdkProxyFactory proxyFactory;

    public StrategyBuilderImpl(@Builtin JdkProxyFactory proxyFactory) {
        this.proxyFactory = proxyFactory;
    }

    @Override
    public <S> S build(StrategyRegistry<S> registry) {
        return createProxy(registry.getAdapterType(), registry);
    }

    @Override
    public <S> S build(Class<S> adapterType, Map<Class<?>, S> registrations) {
        StrategyRegistry<S> registry = StrategyRegistry.newInstance(
            adapterType,
            registrations
        );

        return build(registry);
    }

    @SuppressWarnings("unchecked")
    private <S> S createProxy(
        final Class<S> interfaceType,
        final StrategyRegistry<S> registry
    ) {
        Class<?> interfaceSelectorType = null;

        for (final Method method : interfaceType.getMethods()) {
            Class<?>[] parameterTypes = method.getParameterTypes();
            if (parameterTypes.length == 0) {
                throw new IllegalArgumentException(
                    "Invalid method " +
                        method +
                        ", when using the strategy pattern, every method must take at least the selector as its parameter"
                );
            }
            Class<?> methodSelectorType = parameterTypes[0];
            if (interfaceSelectorType == null) {
                interfaceSelectorType = methodSelectorType;
            } else if (!interfaceSelectorType.equals(methodSelectorType)) {
                throw new IllegalArgumentException(
                    "Conflicting method definitions," +
                        " expecting the first argument of every method to have the same type"
                );
            }
        }

        return (S) Proxy.newProxyInstance(
            proxyFactory.getClassLoader(),
            new Class[] { interfaceType },
            new StrategyInvocationHandler<S>(interfaceType, registry)
        );
    }

    private static class StrategyInvocationHandler<
        S
    > implements InvocationHandler {

        private final Class<S> interfaceType;
        private final StrategyRegistry<S> registry;

        StrategyInvocationHandler(
            Class<S> interfaceType,
            StrategyRegistry<S> registry
        ) {
            this.interfaceType = interfaceType;
            this.registry = registry;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args)
            throws Throwable {
            if (method.getDeclaringClass() == Object.class) {
                return switch (method.getName()) {
                    case "toString" -> String.format(
                        "<Strategy for %s>",
                        interfaceType.getName()
                    );
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> method.invoke(this, args);
                };
            }

            // Argument 0 is the selector
            Object selector = args[0];
            S adapter = registry.getByInstance(selector);

            // Prepare arguments: pass all original args to the adapter method
            return method.invoke(adapter, args);
        }
    }
}
