package com.jujin.freeway.ioc.internal;

import java.lang.invoke.MethodHandle;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * Creates service proxies for advisor/interceptor chains.
 *
 * <p>
 * Supports two mechanisms, selected via the {@code freeway.proxy-mechanism}
 * configuration symbol:
 * </p>
 * <ul>
 * <li>{@code "jdk"} (default) — {@code java.lang.reflect.Proxy} +
 * InvocationHandler. Method dispatch routes through readable Java code; ideal
 * for debugging.</li>
 * <li>{@code "classfile"} — JDK 25 ClassFile API + hidden classes. Each
 * interface method is generated as bytecode that calls the MethodHandle
 * directly, eliminating InvocationHandler indirection.</li>
 * </ul>
 */
public final class ServiceProxyGenerator {

    /** JDK dynamic proxy mechanism. */
    public static final String JDK = "jdk";

    /** ClassFile-API hidden class mechanism. */
    public static final String CLASSFILE = "classfile";

    private static volatile String mechanism;

    static {
        String value = System.getProperty("freeway.proxy-mechanism");
        if (value == null || value.isBlank()) {
            value = System.getenv("FREEWAY_PROXY_MECHANISM");
        }
        mechanism = CLASSFILE.equalsIgnoreCase(value) ? CLASSFILE : JDK;
    }

    private ServiceProxyGenerator() {}

    /**
     * Sets the proxy mechanism globally. Call once during startup. Accepts
     * {@code "jdk"} or {@code "classfile"}.
     */
    public static void setMechanism(String value) {
        if (CLASSFILE.equalsIgnoreCase(value)) {
            mechanism = CLASSFILE;
        } else {
            mechanism = JDK;
        }
    }

    /** Returns the current proxy mechanism. */
    public static String getMechanism() {
        return mechanism;
    }

    public static <T> T createProxy(
        Class<T> interfaceType,
        Object delegate,
        MethodHandle[] handles,
        String description
    ) {
        if (CLASSFILE.equals(mechanism)) {
            return ClassFileProxyGenerator.createProxy(
                interfaceType,
                delegate,
                handles,
                description
            );
        }
        return createJdkProxy(interfaceType, delegate, handles, description);
    }

    @SuppressWarnings("unchecked")
    private static <T> T createJdkProxy(
        Class<T> interfaceType,
        Object delegate,
        MethodHandle[] handles,
        String description
    ) {
        Method[] methods = interfaceType.getMethods();
        if (handles.length != methods.length) {
            throw new IllegalArgumentException(
                "Handle count " +
                    handles.length +
                    " != method count " +
                    methods.length
            );
        }

        return (T) Proxy.newProxyInstance(
            interfaceType.getClassLoader(),
            new Class<?>[] { interfaceType },
            new MethodHandleInvocationHandler(
                delegate,
                handles,
                methods,
                description
            )
        );
    }

    /**
     * InvocationHandler that dispatches each method call to its pre-composed
     * MethodHandle via {@code invokeWithArguments()}.
     */
    private static final class MethodHandleInvocationHandler
        implements InvocationHandler
    {

        private final Object delegate;
        private final MethodHandle[] handles;
        private final Method[] methods;
        private final String description;

        MethodHandleInvocationHandler(
            Object delegate,
            MethodHandle[] handles,
            Method[] methods,
            String description
        ) {
            this.delegate = delegate;
            this.handles = handles;
            this.methods = methods;
            this.description = description;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args)
            throws Throwable {
            // Handle Object methods
            if (method.getDeclaringClass() == Object.class) {
                return switch (method.getName()) {
                    case "toString" -> description;
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> method.invoke(this, args);
                };
            }

            // Find the method index
            for (int i = 0; i < methods.length; i++) {
                if (methods[i].equals(method)) {
                    MethodHandle handle = handles[i];
                    if (handle == null) {
                        return null;
                    }

                    // Build args array: [delegate, param1, param2, ...]
                    Object[] fullArgs;
                    if (args == null || args.length == 0) {
                        fullArgs = new Object[] { delegate };
                    } else {
                        fullArgs = new Object[args.length + 1];
                        fullArgs[0] = delegate;
                        System.arraycopy(args, 0, fullArgs, 1, args.length);
                    }

                    return handle.invoke(fullArgs);
                }
            }

            throw new AbstractMethodError("Unknown method: " + method);
        }
    }
}
