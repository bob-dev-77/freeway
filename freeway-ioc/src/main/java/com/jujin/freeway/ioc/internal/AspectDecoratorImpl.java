package com.jujin.freeway.ioc.internal;

import com.jujin.freeway.ioc.AnnotationAccess;
import com.jujin.freeway.ioc.AnnotationProvider;
import com.jujin.freeway.ioc.advisor.AspectInterceptor;
import com.jujin.freeway.ioc.advisor.AspectInterceptorBuilder;
import com.jujin.freeway.ioc.advisor.MethodAdvice;
import com.jujin.freeway.ioc.annotations.Builtin;
import com.jujin.freeway.ioc.annotations.PreventServiceDecoration;
import com.jujin.freeway.ioc.internal.util.InternalUtils;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

@PreventServiceDecoration
public class AspectDecoratorImpl implements AspectInterceptor {

    private final JdkProxyFactory proxyFactory;

    public AspectDecoratorImpl(@Builtin JdkProxyFactory proxyFactory) {
        this.proxyFactory = proxyFactory;
    }

    @Override
    public <T> AspectInterceptorBuilder<T> createBuilder(
        Class<T> serviceInterface,
        final T delegate,
        String description) {
        return createBuilder(
            serviceInterface,
            delegate,
            new AnnotationAccessImpl(delegate.getClass()),
            description);
    }

    @Override
    public <T> AspectInterceptorBuilder<T> createBuilder(
        final Class<T> serviceInterface,
        final T delegate,
        AnnotationAccess annotationAccess,
        final String description) {
        assert serviceInterface != null;
        assert delegate != null;
        assert InternalUtils.isNonBlank(description);

        // The inner class here defers the creation of the AspectInterceptorBuilderImpl
        // (which now uses MethodHandle + hidden classes via JDK 25 Class-File API)
        // until there is actual advice to apply.

        return new AspectInterceptorBuilder<T>() {
            private final AnnotationAccess aa = annotationAccess;
            private AspectInterceptorBuilder<T> builder;

            @Override
            public AnnotationProvider getClassAnnotationProvider() {
                return aa.getClassAnnotationProvider();
            }

            @Override
            public AnnotationProvider getMethodAnnotationProvider(
                String methodName,
                Class<?>... parameterTypes) {
                return aa.getMethodAnnotationProvider(
                    methodName,
                    parameterTypes);
            }

            @Override
            public <A extends Annotation> A getMethodAnnotation(
                Method method,
                Class<A> annotationType) {
                return getMethodAnnotationProvider(
                    method.getName(),
                    method.getParameterTypes()).getAnnotation(annotationType);
            }

            @Override
            public void adviseMethod(Method method, MethodAdvice advice) {
                getBuilder().adviseMethod(method, advice);
            }

            @Override
            public void adviseAllMethods(MethodAdvice advice) {
                getBuilder().adviseAllMethods(advice);
            }

            @Override
            public Class<?> getInterface() {
                return serviceInterface;
            }

            @Override
            public T build() {
                return builder == null ? delegate : builder.build();
            }

            private AspectInterceptorBuilder<T> getBuilder() {
                if (builder == null)
                    builder = new AspectInterceptorBuilderImpl<T>(
                        annotationAccess,
                        proxyFactory,
                        serviceInterface,
                        delegate,
                        description);

                return builder;
            }
        };
    }
}
