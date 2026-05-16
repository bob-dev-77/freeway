package com.jujin.freeway.ioc.internal.util;

import com.jujin.freeway.ioc.AnnotationProvider;
import com.jujin.freeway.ioc.ServiceLocator;
import com.jujin.freeway.ioc.advisor.OperationTracker;
import com.jujin.freeway.ioc.internal.util.StringUtils;
import com.jujin.freeway.ioc.internal.util.ExceptionUtils;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import javax.inject.Named;

/**
 * Injects values into {@code @Inject}-annotated fields of an already-constructed object.
 * <p>
 * This is used for immediate field injection (e.g., module instances),
 * as opposed to the deferred/planned field injection in {@link InstancePlanBuilder}.
 */
public class FieldInjector {

    /**
     * Injects into the fields (of all visibilities) when the
     * {@link javax.inject.Inject} annotation is present. {@link javax.inject.Named}
     * can be used alongside to specify the service id.
     */
    public static void injectFields(
        Object object,
        ServiceLocator locator,
        InjectionContext resources,
        OperationTracker tracker
    ) {
        Class clazz = object.getClass();
        while (clazz != Object.class) {
            Field[] fields = clazz.getDeclaredFields();
            for (final Field f : fields) {
                int fieldModifiers = f.getModifiers();
                if (
                    Modifier.isStatic(fieldModifiers) ||
                    Modifier.isFinal(fieldModifiers)
                ) continue;

                final var ap = new AnnotationProvider() {
                    @Override
                    public <
                        T extends java.lang.annotation.Annotation
                    > T getAnnotation(Class<T> annotationClass) {
                        return f.getAnnotation(annotationClass);
                    }
                };

                var description = String.format(
                    "Calculating possible injection value for field %s.%s (%s)",
                    clazz.getName(),
                    f.getName(),
                    StringUtils.toSimpleTypeName(f.getType())
                );

                tracker.run(description, () -> {
                    final Class<?> fieldType = f.getType();

                    com.jujin.freeway.ioc.annotations.Inject fwFieldInject =
                        ap.getAnnotation(
                            com.jujin.freeway.ioc.annotations.Inject.class
                        );

                    if (
                        ap.getAnnotation(javax.inject.Inject.class) != null ||
                        fwFieldInject != null
                    ) {
                        if (fwFieldInject != null) {
                            String sid = fwFieldInject.value();
                            if (sid != null && !sid.isEmpty()) {
                                injectField(
                                    object,
                                    f,
                                    locator.getService(sid, fieldType)
                                );
                                return;
                            }
                        }
                        Named named = ap.getAnnotation(Named.class);
                        if (named == null) {
                            Object value = resources.findResource(
                                fieldType,
                                f.getGenericType()
                            );
                            if (value != null) {
                                injectField(object, f, value);
                                return;
                            }
                            injectField(
                                object,
                                f,
                                locator.getObject(fieldType, ap)
                            );
                        } else {
                            injectField(
                                object,
                                f,
                                locator.getService(named.value(), fieldType)
                            );
                        }
                    }
                });
            }
            clazz = clazz.getSuperclass();
        }
    }

    private static void injectField(Object target, Field field, Object value) {
        try {
            MethodHandleUtils.varHandle(field).set(target, value);
        } catch (Exception ex) {
            throw new RuntimeException(
                String.format(
                    "Unable to set field '%s' of %s to %s: %s",
                    field.getName(),
                    target,
                    value,
                    ExceptionUtils.toMessage(ex)
                )
            );
        }
    }

    private FieldInjector() {}
}
