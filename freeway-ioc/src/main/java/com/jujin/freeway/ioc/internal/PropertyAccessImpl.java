package com.jujin.freeway.ioc.internal;

import com.jujin.freeway.ioc.config.*;
import com.jujin.freeway.ioc.property.*;
import com.jujin.freeway.ioc.threading.*;
import com.jujin.freeway.ioc.classpath.*;
import com.jujin.freeway.ioc.exception.*;
import com.jujin.freeway.ioc.property.BeanPropertyAdapter;
import com.jujin.freeway.ioc.config.*;
import com.jujin.freeway.ioc.property.*;
import com.jujin.freeway.ioc.threading.*;
import com.jujin.freeway.ioc.classpath.*;
import com.jujin.freeway.ioc.exception.*;
import com.jujin.freeway.ioc.property.PropertyAccess;
import com.jujin.freeway.ioc.config.*;
import com.jujin.freeway.ioc.property.*;
import com.jujin.freeway.ioc.threading.*;
import com.jujin.freeway.ioc.classpath.*;
import com.jujin.freeway.ioc.exception.*;
import com.jujin.freeway.ioc.property.PropertyAdapter;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.annotation.Annotation;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Minimal implementation of {@link PropertyAccess} using standard Java Beans
 * introspection and reflection. Replaces the beanmodel-based implementation.
 */
@SuppressWarnings("rawtypes")
public class PropertyAccessImpl implements PropertyAccess {
    private final ConcurrentHashMap<Class, BeanPropertyAdapter> cache = new ConcurrentHashMap<>();

    @Override
    public Object get(Object instance, String propertyName) {
        return getAdapter(instance).get(instance, propertyName);
    }

    @Override
    public void set(Object instance, String propertyName, Object value) {
        getAdapter(instance).set(instance, propertyName, value);
    }

    @Override
    public Annotation getAnnotation(Object instance, String propertyName, Class<? extends Annotation> annotationClass) {
        return getAdapter(instance).getAnnotation(instance, propertyName, annotationClass);
    }

    @Override
    public BeanPropertyAdapter getAdapter(Object instance) {
        return getAdapter(instance.getClass());
    }

    @Override
    public BeanPropertyAdapter getAdapter(Class forClass) {
        BeanPropertyAdapter adapter = cache.get(forClass);

        if (adapter == null) {
            adapter = new BeanPropertyAdapterImpl(forClass);
            BeanPropertyAdapter existing = cache.putIfAbsent(forClass, adapter);

            if (existing != null)
                adapter = existing;
        }

        return adapter;
    }

    @Override
    public void clearCache() {
        cache.clear();
    }

    private static class BeanPropertyAdapterImpl implements BeanPropertyAdapter {
        private final Class clazz;
        private final Map<String, PropertyDescriptor> descriptors = new HashMap<>();
        private final Map<String, Field> publicFields = new HashMap<>();
        private final Map<String, MethodHandle> readHandles = new HashMap<>();
        private final Map<String, MethodHandle> writeHandles = new HashMap<>();
        private final Map<String, VarHandle> fieldVarHandles = new HashMap<>();

        BeanPropertyAdapterImpl(Class clazz) {
            this.clazz = clazz;

            try {
                BeanInfo beanInfo = Introspector.getBeanInfo(clazz);

                for (PropertyDescriptor pd : beanInfo.getPropertyDescriptors()) {
                    if (pd.getReadMethod() != null || pd.getWriteMethod() != null) {
                        descriptors.put(pd.getName(), pd);
                    }
                }
            } catch (IntrospectionException e) {
                // ignore, fall back to public fields
            }

            // Also scan for public fields
            for (Field field : clazz.getFields()) {
                int mod = field.getModifiers();
                if (java.lang.reflect.Modifier.isPublic(mod) && !java.lang.reflect.Modifier.isStatic(mod)) {
                    publicFields.put(field.getName(), field);
                }
            }

            // Build MethodHandles for bean properties (public lookup is safe: all
            // getters/setters are public)
            try {
                for (var entry : descriptors.entrySet()) {
                    var pd = entry.getValue();
                    if (pd.getReadMethod() != null)
                        readHandles.put(entry.getKey(), MethodHandles.publicLookup().unreflect(pd.getReadMethod()));
                    if (pd.getWriteMethod() != null)
                        writeHandles.put(entry.getKey(), MethodHandles.publicLookup().unreflect(pd.getWriteMethod()));
                }
                for (var entry : publicFields.entrySet()) {
                    fieldVarHandles.put(entry.getKey(),
                        MethodHandles.publicLookup().unreflectVarHandle(entry.getValue()));
                }
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public List<String> getPropertyNames() {
            List<String> names = new java.util.ArrayList<>(descriptors.keySet());
            names.addAll(publicFields.keySet());
            java.util.Collections.sort(names);
            return java.util.Collections.unmodifiableList(names);
        }

        @Override
        public Class getBeanType() {
            return clazz;
        }

        @Override
        public PropertyAdapter getPropertyAdapter(String name) {
            // Return a simple adapter based on reflection
            PropertyDescriptor pd = descriptors.get(name);
            if (pd != null)
                return new ReflectionPropertyAdapter(clazz, pd);

            Field f = publicFields.get(name);
            if (f != null)
                return new ReflectionPropertyAdapter(clazz, f);

            return null;
        }

        public Class getPropertyType(String propertyName) {
            PropertyDescriptor pd = descriptors.get(propertyName);
            if (pd != null)
                return pd.getPropertyType();

            Field f = publicFields.get(propertyName);
            if (f != null)
                return f.getType();

            return null;
        }

        @Override
        public Object get(Object instance, String propertyName) {
            var readHandle = readHandles.get(propertyName);
            if (readHandle != null) {
                try {
                    return readHandle.invoke(instance);
                } catch (Throwable e) {
                    throw new RuntimeException(e);
                }
            }

            var fHandle = fieldVarHandles.get(propertyName);
            if (fHandle != null) {
                try {
                    return fHandle.get(instance);
                } catch (Throwable e) {
                    throw new RuntimeException(e);
                }
            }

            throw new RuntimeException(
                String.format("Class %s does not have a property named '%s'.", clazz.getName(), propertyName));
        }

        @Override
        public void set(Object instance, String propertyName, Object value) {
            var writeHandle = writeHandles.get(propertyName);
            if (writeHandle != null) {
                try {
                    writeHandle.invoke(instance, value);
                    return;
                } catch (Throwable e) {
                    throw new RuntimeException(e);
                }
            }

            var fHandle = fieldVarHandles.get(propertyName);
            if (fHandle != null) {
                try {
                    fHandle.set(instance, value);
                    return;
                } catch (Throwable e) {
                    throw new RuntimeException(e);
                }
            }

            throw new RuntimeException(
                String.format("Class %s does not have a property named '%s'.", clazz.getName(), propertyName));
        }

        @Override
        public Annotation getAnnotation(Object instance, String propertyName,
            Class<? extends Annotation> annotationClass) {
            PropertyDescriptor pd = descriptors.get(propertyName);
            if (pd != null) {
                Method readMethod = pd.getReadMethod();
                if (readMethod != null && readMethod.isAnnotationPresent(annotationClass))
                    return readMethod.getAnnotation(annotationClass);

                Method writeMethod = pd.getWriteMethod();
                if (writeMethod != null && writeMethod.isAnnotationPresent(annotationClass))
                    return writeMethod.getAnnotation(annotationClass);
            }

            Field f = publicFields.get(propertyName);
            if (f != null && f.isAnnotationPresent(annotationClass))
                return f.getAnnotation(annotationClass);

            return null;
        }

    }

    private static class ReflectionPropertyAdapter implements PropertyAdapter {
        private final Class clazz;
        private final String name;
        private final Method readMethod;
        private final Method writeMethod;
        private final Field field;
        private final Class type;
        private final MethodHandle readHandle;
        private final MethodHandle writeHandle;
        private final VarHandle fieldHandle;

        ReflectionPropertyAdapter(Class clazz, PropertyDescriptor pd) {
            this.clazz = clazz;
            this.name = pd.getName();
            this.readMethod = pd.getReadMethod();
            this.writeMethod = pd.getWriteMethod();
            this.field = null;
            this.type = pd.getPropertyType();

            MethodHandle rh = null, wh = null;
            try {
                if (readMethod != null)
                    rh = MethodHandles.publicLookup().unreflect(readMethod);
                if (writeMethod != null)
                    wh = MethodHandles.publicLookup().unreflect(writeMethod);
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
            this.readHandle = rh;
            this.writeHandle = wh;
            this.fieldHandle = null;
        }

        ReflectionPropertyAdapter(Class clazz, Field field) {
            this.clazz = clazz;
            this.name = field.getName();
            this.readMethod = null;
            this.writeMethod = null;
            this.field = field;
            this.type = field.getType();

            VarHandle fh = null;
            try {
                fh = MethodHandles.publicLookup().unreflectVarHandle(field);
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
            this.readHandle = null;
            this.writeHandle = null;
            this.fieldHandle = fh;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public boolean isRead() {
            return readMethod != null || field != null;
        }

        @Override
        public Method getReadMethod() {
            return readMethod;
        }

        @Override
        public boolean isUpdate() {
            return writeMethod != null || (field != null && !java.lang.reflect.Modifier.isFinal(field.getModifiers()));
        }

        @Override
        public Method getWriteMethod() {
            return writeMethod;
        }

        @Override
        public Object get(Object instance) {
            try {
                if (readHandle != null)
                    return readHandle.invoke(instance);
                if (fieldHandle != null)
                    return fieldHandle.get(instance);
                throw new UnsupportedOperationException("Property " + name + " is not readable.");
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void set(Object instance, Object value) {
            try {
                if (writeHandle != null) {
                    writeHandle.invoke(instance, value);
                    return;
                }
                if (fieldHandle != null) {
                    fieldHandle.set(instance, value);
                    return;
                }
                throw new UnsupportedOperationException("Property " + name + " is not writable.");
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public Class getType() {
            return type;
        }

        @Override
        public boolean isCastRequired() {
            return false;
        }

        @Override
        public BeanPropertyAdapter getClassAdapter() {
            return null;
        }

        @Override
        public Class getBeanType() {
            return clazz;
        }

        @Override
        public boolean isField() {
            return field != null;
        }

        @Override
        public Field getField() {
            return field;
        }

        @Override
        public Class getDeclaringClass() {
            return clazz;
        }

        @Override
        public <T extends java.lang.annotation.Annotation> T getAnnotation(Class<T> annotationClass) {
            if (readMethod != null && readMethod.isAnnotationPresent(annotationClass))
                return readMethod.getAnnotation(annotationClass);
            if (writeMethod != null && writeMethod.isAnnotationPresent(annotationClass))
                return writeMethod.getAnnotation(annotationClass);
            if (field != null && field.isAnnotationPresent(annotationClass))
                return field.getAnnotation(annotationClass);
            return null;
        }
    }
}
