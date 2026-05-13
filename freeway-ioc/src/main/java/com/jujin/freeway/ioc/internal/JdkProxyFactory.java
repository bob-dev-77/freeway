package com.jujin.freeway.ioc.internal;

import com.jujin.freeway.ioc.lifecycle.ObjectCreator;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * A factory for creating JDK dynamic proxies, serving as a replacement for
 * PlasticProxyFactory that does not rely on bytecode generation.
 *
 * <p>
 * This factory creates proxies that delegate all method invocations through
 * pre-composed {@link MethodHandle}s for JIT-compilable dispatch.
 * </p>
 */
public class JdkProxyFactory {

    private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();

    /** Cache of composed handles keyed by method. Cleared on class reload. */
    private static final ConcurrentMap<Method, MethodHandle> HANDLE_CACHE = new ConcurrentHashMap<>();
    private static final int MAX_CACHE_SIZE = 1024;

    private final ClassLoader classLoader;

    public JdkProxyFactory() {
        this(Thread.currentThread().getContextClassLoader());
    }

    public JdkProxyFactory(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    public ClassLoader getClassLoader() {
        return classLoader;
    }

    @SuppressWarnings("unchecked")
    public <T> T createProxy(Class<T> interfaceType, ObjectCreator<T> creator, String description) {
        return (T) Proxy.newProxyInstance(
            classLoader,
            new Class[]{ interfaceType },
            new ObjectCreatorInvocationHandler<>(creator, description));
    }

    public void clearCache() {
        HANDLE_CACHE.clear();
    }

    public JdkProxyFactory getProxyFactory(String className) {
        return this;
    }

    /**
     * Invocation handler that delegates all calls through a MethodHandle. On first
     * method call, obtains or composes a MethodHandle with the delegate bound; all
     * subsequent calls to the same method reuse it.
     */
    private static class ObjectCreatorInvocationHandler<T> implements InvocationHandler {
        private final ObjectCreator<T> creator;
        private final String description;
        private final ConcurrentMap<Method, MethodHandle> boundCache = new ConcurrentHashMap<>();

        ObjectCreatorInvocationHandler(ObjectCreator<T> creator, String description) {
            this.creator = creator;
            this.description = description;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (method.getDeclaringClass() == Object.class) {
                return switch (method.getName()) {
                    case "toString" -> description;
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> method.invoke(this, args);
                };
            }

            T delegate = creator.create();
            MethodHandle mh = boundCache.computeIfAbsent(method, m -> composeHandle(m, delegate));
            return invokeHandle(mh, args, method.getParameterCount());
        }

        private static Object invokeHandle(MethodHandle raw, Object[] args, int paramCount) throws Throwable {
            // Adapt: handle void returns (box to null) and primitive returns (box to
            // wrapper)
            if (paramCount == 0) {
                MethodHandle adapted = raw.asType(MethodType.methodType(Object.class));
                // Accept an Object[] (ignored) for uniform call signature
                return MethodHandles.dropArguments(adapted, 0, Object[].class)
                    .invoke(args != null ? args : new Object[0]);
            }
            return raw.asSpreader(Object[].class, paramCount)
                .asType(MethodType.methodType(Object.class, Object[].class))
                .invoke(args);
        }
    }

    /**
     * Composes a method handle from a reflective Method using the common LOOKUP.
     */
    private static MethodHandle composeHandle(Method method, Object target) {
        if (HANDLE_CACHE.size() > MAX_CACHE_SIZE) {
            HANDLE_CACHE.clear();
        }
        return HANDLE_CACHE.computeIfAbsent(method, m -> {
            try {
                MethodHandles.Lookup lookup = suitableLookup(m.getDeclaringClass());
                return lookup.unreflect(m);
            } catch (IllegalAccessException e) {
                throw new RuntimeException("Cannot access method: " + m, e);
            }
        }).bindTo(target);
    }

    private static MethodHandles.Lookup suitableLookup(Class<?> clazz) {
        if (clazz.getModule().isNamed())
            return LOOKUP;
        try {
            return MethodHandles.privateLookupIn(clazz, LOOKUP);
        } catch (IllegalAccessException e) {
            return LOOKUP;
        }
    }
}
