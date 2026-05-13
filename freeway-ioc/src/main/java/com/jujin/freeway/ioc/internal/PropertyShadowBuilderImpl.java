package com.jujin.freeway.ioc.internal;

import com.jujin.freeway.ioc.annotations.Builtin;
import com.jujin.freeway.ioc.property.PropertyAccess;
import com.jujin.freeway.ioc.property.PropertyAdapter;
import com.jujin.freeway.ioc.property.PropertyShadowBuilder;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

public class PropertyShadowBuilderImpl implements PropertyShadowBuilder {

    private final PropertyAccess propertyAccess;

    private final JdkProxyFactory proxyFactory;

    public PropertyShadowBuilderImpl(
        @Builtin JdkProxyFactory proxyFactory,
        PropertyAccess propertyAccess) {
        this.proxyFactory = proxyFactory;
        this.propertyAccess = propertyAccess;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T build(
        final Object source,
        final String propertyName,
        final Class<T> propertyType) {
        final Class<?> sourceClass = source.getClass();
        final PropertyAdapter adapter = propertyAccess
            .getAdapter(sourceClass)
            .getPropertyAdapter(propertyName);

        if (adapter == null)
            throw new RuntimeException(
                ServiceMessages.noSuchProperty(sourceClass, propertyName));

        if (!adapter.isRead()) {
            throw new RuntimeException(
                String.format(
                    "Class %s does not provide an accessor ('getter') method for property '%s'.",
                    source.getClass().getName(),
                    propertyName));
        }

        if (!propertyType.isAssignableFrom(adapter.getType()))
            throw new RuntimeException(
                ServiceMessages.propertyTypeMismatch(
                    propertyName,
                    sourceClass,
                    adapter.getType(),
                    propertyType));

        return (T) Proxy.newProxyInstance(
            proxyFactory.getClassLoader(),
            new Class[]{ propertyType },
            new PropertyShadowInvocationHandler(
                source,
                propertyName,
                adapter.getReadMethod()));
    }

    private static class PropertyShadowInvocationHandler
        implements InvocationHandler {

        private final Object source;
        private final String propertyName;
        private final java.lang.reflect.Method readMethod;

        PropertyShadowInvocationHandler(
            Object source,
            String propertyName,
            java.lang.reflect.Method readMethod) {
            this.source = source;
            this.propertyName = propertyName;
            this.readMethod = readMethod;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args)
            throws Throwable {
            if (method.getDeclaringClass() == Object.class) {
                return switch (method.getName()) {
                    case "toString" -> String.format(
                        "<Shadow: property %s of %s>",
                        propertyName,
                        source);
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> method.invoke(this, args);
                };
            }

            Object propertyValue = readMethod.invoke(source);

            if (propertyValue == null)
                throw new NullPointerException(
                    String.format(
                        "Unable to delegate method invocation to property '%s' of %s, because the property is null.",
                        propertyName,
                        source));

            return method.invoke(propertyValue, args);
        }
    }
}
