package com.jujin.freeway.ioc.advisor.internal;

import com.jujin.freeway.ioc.AnnotationAccess;
import com.jujin.freeway.ioc.AnnotationProvider;
import com.jujin.freeway.ioc.advisor.AspectInterceptorBuilder;
import com.jujin.freeway.ioc.advisor.MethodAdvice;
import com.jujin.freeway.ioc.internal.JdkProxyFactory;
import com.jujin.freeway.ioc.internal.MethodHandleMethodInvocation;
import com.jujin.freeway.ioc.internal.ServiceProxyGenerator;
import java.lang.annotation.Annotation;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;

/**
 * JDK 25 native implementation of
 * {@link com.jujin.freeway.ioc.advisor.AspectInterceptorBuilder}.
 *
 * <p>
 * Replaces {@code java.lang.reflect.Proxy} + reflective {@code method.invoke()}
 * with {@link MethodHandle} and {@link ServiceProxyGenerator} (hidden classes
 * via Class-File API). The delegate invocation path is now fully
 * JIT-compilable.
 * </p>
 */
@SuppressWarnings({ "rawtypes", "unchecked" })
public class AspectInterceptorBuilderImpl<
    T
> implements AspectInterceptorBuilder<T> {

    private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();

    private final AnnotationAccess annotationAccess;
    private final Class<T> serviceInterface;
    private final Set<Method> allMethods = new HashSet<>();
    private final Map<Method, List<MethodAdvice>> methodAdvices =
        new HashMap<>();
    private final T delegate;
    private final String description;
    private final JdkProxyFactory proxyFactory;

    public AspectInterceptorBuilderImpl(
        AnnotationAccess annotationAccess,
        JdkProxyFactory proxyFactory,
        Class<T> serviceInterface,
        T delegate,
        String description
    ) {
        this.annotationAccess = annotationAccess;
        this.serviceInterface = serviceInterface;
        this.delegate = delegate;
        this.description = description;
        this.proxyFactory = proxyFactory;
        allMethods.addAll(Arrays.asList(serviceInterface.getMethods()));
    }

    @Override
    public AnnotationProvider getClassAnnotationProvider() {
        return annotationAccess.getClassAnnotationProvider();
    }

    @Override
    public AnnotationProvider getMethodAnnotationProvider(
        String methodName,
        Class<?>... parameterTypes
    ) {
        return annotationAccess.getMethodAnnotationProvider(
            methodName,
            parameterTypes
        );
    }

    @Override
    public <A extends Annotation> A getMethodAnnotation(
        Method method,
        Class<A> annotationType
    ) {
        return getMethodAnnotationProvider(
            method.getName(),
            method.getParameterTypes()
        ).getAnnotation(annotationType);
    }

    @Override
    public void adviseMethod(Method method, MethodAdvice advice) {
        assert method != null;
        assert advice != null;
        if (!allMethods.contains(method)) {
            throw new IllegalArgumentException(
                String.format(
                    "Method %s is not defined for interface %s.",
                    method,
                    serviceInterface
                )
            );
        }
        methodAdvices
            .computeIfAbsent(method, k -> new ArrayList<>())
            .add(advice);
    }

    @Override
    public void adviseAllMethods(MethodAdvice advice) {
        for (Method m : serviceInterface.getMethods()) {
            if (!Modifier.isStatic(m.getModifiers())) {
                adviseMethod(m, advice);
            }
        }
    }

    @Override
    public Class getInterface() {
        return serviceInterface;
    }

    @Override
    @SuppressWarnings("unchecked")
    public T build() {
        Method[] methods = serviceInterface.getMethods();
        MethodHandle[] handles = new MethodHandle[methods.length];

        for (int i = 0; i < methods.length; i++) {
            Method method = methods[i];
            if (method.getDeclaringClass() == Object.class) {
                handles[i] = null;
                continue;
            }
            handles[i] = composeMethodHandle(method);
        }

        return ServiceProxyGenerator.createProxy(
            serviceInterface,
            delegate,
            handles,
            description
        );
    }

    /**
     * Composes a {@code (Object[])Object} MethodHandle for a service method.
     *
     * <p>
     * The returned handle expects args where args[0] is the delegate and args[1..N]
     * are the method parameters. It may include an advice chain wrapping the
     * delegate call.
     * </p>
     */
    private MethodHandle composeMethodHandle(Method method) {
        try {
            MethodHandle rawHandle = LOOKUP.unreflect(method);

            // The raw handle signature is (Object, paramTypes...)RetType.
            // We need (Object[])Object for uniform proxy dispatch.
            int paramCount = rawHandle.type().parameterCount(); // includes delegate
            MethodHandle spread = rawHandle.asSpreader(
                Object[].class,
                paramCount
            );
            MethodHandle boxed = spread.asType(
                MethodType.methodType(Object.class, Object[].class)
            );

            List<MethodAdvice> advices = methodAdvices.get(method);
            if (advices == null || advices.isEmpty()) {
                return boxed;
            }

            // Build the delegate handle (without spreading): (Object, params...)RetType
            // This is used inside MethodInvocation.proceed() for the actual method call.
            // We bind the delegate at invocation time from args[0].
            MethodHandle typedHandle = rawHandle.asType(
                MethodType.methodType(
                    Object.class,
                    rawHandle.type().parameterArray()
                )
            );

            // Create an invoker that bridges advice chain → MethodHandle
            AdviceChainInvoker invoker = new AdviceChainInvoker(
                method,
                typedHandle,
                new ArrayList<>(advices)
            );

            MethodHandle invokerHandle = LOOKUP.findVirtual(
                AdviceChainInvoker.class,
                "invoke",
                MethodType.methodType(Object.class, Object[].class)
            );

            return invokerHandle.bindTo(invoker);
        } catch (Exception e) {
            throw new RuntimeException(
                "Failed to compose MethodHandle for " + method,
                e
            );
        }
    }

    /**
     * Bridges the {@link MethodAdvice} callback-based API to MethodHandle
     * invocation.
     *
     * <p>
     * Each proxy method call goes through {@link #invoke(Object[])}, which:
     * <ol>
     * <li>Extracts method parameters from the args array</li>
     * <li>Creates a {@link MethodHandleMethodInvocation}</li>
     * <li>Calls {@code proceed()} to start the advice chain</li>
     * <li>Returns the result (or throws the checked exception)</li>
     * </ol>
     * </p>
     */
    private static class AdviceChainInvoker {

        private final Method method;
        private final MethodHandle delegateHandle; // (Object, params...)Object
        private final List<MethodAdvice> advices;

        AdviceChainInvoker(
            Method method,
            MethodHandle delegateHandle,
            List<MethodAdvice> advices
        ) {
            this.method = method;
            this.delegateHandle = delegateHandle;
            this.advices = advices;
        }

        @SuppressWarnings("unused") // called via MethodHandle
        Object invoke(Object[] args) throws Throwable {
            // args[0] = delegate, args[1..N] = method parameters
            Object[] params = new Object[args.length - 1];
            System.arraycopy(args, 1, params, 0, params.length);

            // Re-bind the delegate from args[0] for this invocation
            MethodHandle bound = delegateHandle.bindTo(args[0]);

            MethodHandleMethodInvocation invocation =
                new MethodHandleMethodInvocation(
                    method,
                    params,
                    bound,
                    advices
                );

            invocation.proceed();

            if (invocation.didThrowCheckedException()) {
                throw invocation.getCheckedException(Exception.class);
            }

            return invocation.getReturnValue();
        }
    }
}
