package com.jujin.freeway.ioc.internal.util;

import com.jujin.freeway.ioc.AnnotationProvider;
import com.jujin.freeway.ioc.ServiceLocator;
import com.jujin.freeway.ioc.advisor.OperationTracker;
import com.jujin.freeway.ioc.annotations.Autobuild;
import com.jujin.freeway.ioc.lifecycle.ObjectCreator;
import java.lang.annotation.Annotation;
import java.lang.invoke.MethodHandle;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.inject.Named;

/**
 * Resolves injection values for parameters and fields.
 * <p>
 * Determines how each parameter/field gets its value based on annotations
 * ({@code @Named}, {@code @Inject}, {@code @Autobuild}) and falls back to
 * the {@code ObjectInjector} chain when no specific directive is given.
 */
public class InjectionPlanner {

    private static final AnnotationProvider NULL_ANNOTATION_PROVIDER =
        new AnnotationProvider() {
            @Override
            public <T extends Annotation> T getAnnotation(
                Class<T> annotationClass
            ) {
                return null;
            }
        };

    private static final Function<ObjectCreator, Object> CREATE_OBJECT =
        ObjectCreator::create;

    // ── Helpers ─────────────────────────────────────────────────────

    static <T extends Annotation> T findAnnotation(
        Annotation[] annotations,
        Class<T> annotationClass
    ) {
        for (Annotation a : annotations) {
            if (annotationClass.isInstance(a)) return annotationClass.cast(a);
        }
        return null;
    }

    private static ObjectCreator<Object> asObjectCreator(
        final Object fixedValue
    ) {
        return () -> fixedValue;
    }

    // ── Core resolution ─────────────────────────────────────────────

    /**
     * Decides how to resolve a single parameter/field value based on its annotations.
     * Returns an {@link ObjectCreator} that will produce the value when {@code create()} is called.
     */
    static ObjectCreator resolveParameter(
        final Class injectionType,
        Type genericType,
        final Annotation[] annotations,
        final ServiceLocator locator,
        InjectionContext resources
    ) {
        final var provider = new AnnotationProvider() {
            @Override
            public <T extends Annotation> T getAnnotation(
                Class<T> annotationClass
            ) {
                return findAnnotation(annotations, annotationClass);
            }
        };

        Named named = provider.getAnnotation(Named.class);
        if (named != null) {
            return asObjectCreator(
                locator.getService(named.value(), injectionType)
            );
        }

        com.jujin.freeway.ioc.annotations.Inject fwInject =
            provider.getAnnotation(
                com.jujin.freeway.ioc.annotations.Inject.class
            );

        if (fwInject != null) {
            String sid = fwInject.value();
            if (sid != null && !sid.isEmpty()) {
                return asObjectCreator(locator.getService(sid, injectionType));
            }
        }

        if (
            provider.getAnnotation(javax.inject.Inject.class) == null &&
            fwInject == null
        ) {
            Object result = resources.findResource(injectionType, genericType);
            if (result != null) {
                return asObjectCreator(result);
            }
        }

        if (provider.getAnnotation(Autobuild.class) != null) {
            return () -> locator.getObject(injectionType, provider);
        }

        return asObjectCreator(locator.getObject(injectionType, provider));
    }

    /**
     * Creates an array of {@link ObjectCreator}s for all parameters of a method,
     * using the service locator and injection context.
     */
    public static ObjectCreator[] resolveMethodParameters(
        Method method,
        ServiceLocator locator,
        InjectionContext resources,
        OperationTracker tracker
    ) {
        return resolveParameters(
            locator,
            resources,
            method.getParameterTypes(),
            method.getGenericParameterTypes(),
            method.getParameterAnnotations(),
            tracker
        );
    }

    /**
     * Creates an array of {@link ObjectCreator}s for the given parameter types,
     * generic types, and annotations.
     */
    public static ObjectCreator[] resolveParameters(
        final ServiceLocator locator,
        final InjectionContext resources,
        Class[] parameterTypes,
        final Type[] genericTypes,
        Annotation[][] parameterAnnotations,
        OperationTracker tracker
    ) {
        int count = parameterTypes.length;
        ObjectCreator[] parameters = new ObjectCreator[count];

        for (int i = 0; i < count; i++) {
            final Class type = parameterTypes[i];
            final Type genericType = genericTypes[i];
            final Annotation[] annotations = parameterAnnotations[i];

            var description = String.format(
                "Determining injection value for parameter #%d (%s)",
                i + 1,
                InternalUtils.toSimpleTypeName(type)
            );

            final Supplier<ObjectCreator> operation = () ->
                resolveParameter(
                    type,
                    genericType,
                    annotations,
                    locator,
                    resources
                );

            parameters[i] = tracker.invoke(description, operation);
        }

        return parameters;
    }

    /**
     * Realizes (creates) all ObjectCreators, returning an array of actual values.
     */
    public static Object[] realizeAll(ObjectCreator[] creators) {
        return Arrays.stream(creators)
            .map(CREATE_OBJECT)
            .toArray(Object[]::new);
    }

    /**
     * Invokes a constructor via its MethodHandle, realizing the constructor parameters first.
     */
    @SuppressWarnings("unchecked")
    public static <T> T invokeConstructor(
        MethodHandle constructorHandle,
        ObjectCreator<?>[] constructorParameters
    ) {
        Object[] realized = realizeAll(constructorParameters);
        try {
            return (T) constructorHandle.invokeWithArguments(realized);
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable t) {
            throw new RuntimeException(
                String.format(
                    "Error invoking constructor via MethodHandle: %s",
                    InternalUtils.toMessage(t)
                ),
                t
            );
        }
    }

    private InjectionPlanner() {}
}
