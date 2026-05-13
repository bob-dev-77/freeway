package com.jujin.freeway.ioc.internal.util;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * JDK 25 native utilities that replace reflective
 * {@code constructor.newInstance()}, {@code method.invoke()}, and
 * {@code field.set()} with {@link MethodHandle} and {@link VarHandle}, which
 * are fully JIT-compilable.
 *
 * <p>
 * All handles are cached by identity of the underlying reflective object.
 * </p>
 */
public final class MethodHandleUtils {

    private static final MethodHandles.Lookup BASE_LOOKUP = MethodHandles.lookup();

    private static final ConcurrentMap<Constructor<?>, MethodHandle> CTOR_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentMap<MethodKey, MethodHandle> METHOD_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentMap<Field, VarHandle> VARHANDLE_CACHE = new ConcurrentHashMap<>();

    private MethodHandleUtils() {}

    /**
     * Returns a cached MethodHandle for the given constructor. Use
     * {@code handle.invokeWithArguments(Object[])} to invoke.
     */
    public static MethodHandle constructorHandle(Constructor<?> constructor) {
        return CTOR_CACHE.computeIfAbsent(constructor, c -> {
            try {
                return BASE_LOOKUP.unreflectConstructor(c);
            } catch (IllegalAccessException e) {
                throw new RuntimeException("Cannot access constructor: " + c, e);
            }
        });
    }

    /**
     * Returns a cached MethodHandle for the given method.
     */
    public static MethodHandle methodHandle(Method method) {
        MethodKey key = new MethodKey(method);
        return METHOD_CACHE.computeIfAbsent(key, k -> {
            try {
                return suitableLookup(method.getDeclaringClass()).unreflect(method);
            } catch (IllegalAccessException e) {
                throw new RuntimeException("Cannot access method: " + method, e);
            }
        });
    }

    /**
     * Returns a Lookup that can access the given class's members. For JDK classes,
     * uses the base lookup (public access only).
     */
    private static MethodHandles.Lookup suitableLookup(Class<?> clazz) {
        if (clazz.getModule().isNamed()) {
            return BASE_LOOKUP;
        }
        try {
            return MethodHandles.privateLookupIn(clazz, BASE_LOOKUP);
        } catch (IllegalAccessException e) {
            return BASE_LOOKUP;
        }
    }

    /**
     * Returns a cached VarHandle for the given field, with private write access.
     */
    public static VarHandle varHandle(Field field) {
        return VARHANDLE_CACHE.computeIfAbsent(field, f -> {
            try {
                MethodHandles.Lookup lookup = suitableLookup(f.getDeclaringClass());
                return lookup.findVarHandle(f.getDeclaringClass(), f.getName(), f.getType());
            } catch (Exception e) {
                throw new RuntimeException("Cannot create VarHandle for field: " + f, e);
            }
        });
    }

    private static final class MethodKey {
        private final Method method;
        private final int hashCode;

        MethodKey(Method method) {
            this.method = method;
            this.hashCode = method.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof MethodKey mk && mk.method.equals(method);
        }

        @Override
        public int hashCode() {
            return hashCode;
        }
    }
}
