package com.jujin.freeway.ioc.internal.util;

import com.jujin.freeway.ioc.AnnotationProvider;
import com.jujin.freeway.ioc.ServiceLocator;
import com.jujin.freeway.ioc.annotations.ServiceId;
import java.io.Closeable;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.*;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.inject.Named;

/**
 * Reflection, class-loading, annotation-processing and instantiation utilities.
 */
public class ReflectionUtils {

    /** A null-object AnnotationProvider that always returns null. */
    public static final AnnotationProvider NULL_ANNOTATION_PROVIDER =
        new AnnotationProvider() {
            @Override
            public <T extends Annotation> T getAnnotation(
                Class<T> annotationClass
            ) {
                return null;
            }
        };

    /** Maps a Class to an AnnotationProvider. */
    public static final Function<Class, AnnotationProvider> CLASS_TO_AP_MAPPER =
        ReflectionUtils::toAnnotationProvider;

    /** Maps a Method to an AnnotationProvider. */
    public static final Function<
        Method,
        AnnotationProvider
    > METHOD_TO_AP_MAPPER = ReflectionUtils::toAnnotationProvider;

    // ── Constructor utilities ───────────────────────────────────────

    public static Constructor findAutobuildConstructor(Class clazz) {
        Constructor[] ctors = clazz.getConstructors();
        switch (ctors.length) {
            case 1:
                return ctors[0];
            case 0:
                return null;
            default:
                break;
        }
        var std = findConstructorByAnnotation(ctors, javax.inject.Inject.class);
        if (std != null) return std;
        var fw = findConstructorByAnnotation(
            ctors,
            com.jujin.freeway.ioc.annotations.Inject.class
        );
        if (fw != null) return fw;
        Arrays.sort(
            ctors,
            (a, b) ->
                b.getParameterTypes().length - a.getParameterTypes().length
        );
        return ctors[0];
    }

    private static <
        T extends Annotation
    > Constructor findConstructorByAnnotation(
        Constructor[] ctors,
        Class<T> ann
    ) {
        for (Constructor c : ctors) {
            if (c.getAnnotation(ann) != null) return c;
        }
        return null;
    }

    public static void validateConstructorForAutobuild(
        Constructor constructor
    ) {
        Class clazz = constructor.getDeclaringClass();
        if (
            !Modifier.isPublic(clazz.getModifiers())
        ) throw new IllegalArgumentException(
            String.format(
                "Class %s is not a public class and may not be autobuilt.",
                clazz.getName()
            )
        );
        if (
            !Modifier.isPublic(constructor.getModifiers())
        ) throw new IllegalArgumentException(
            String.format(
                "Constructor %s is not public and may not be used for autobuilding.",
                constructor
            )
        );
    }

    // ── Method / class utilities ────────────────────────────────────

    public static boolean isStatic(Method method) {
        return Modifier.isStatic(method.getModifiers());
    }

    public static boolean isLocalFile(Class clazz) {
        var path = clazz.getName().replace('.', '/') + ".class";
        ClassLoader loader = clazz.getClassLoader();
        if (loader == null) return false;
        var url = loader.getResource(path);
        return url != null && url.getProtocol().equals("file");
    }

    public static <T> T instantiate(
        Class<T> contributionType,
        ServiceLocator locator,
        Class<? extends T> clazz
    ) {
        assert clazz != null;
        if (
            contributionType.isInterface() &&
            isLocalFile(clazz) &&
            contributionType.isAssignableFrom(clazz)
        ) return locator.proxy(contributionType, clazz);
        return locator.autobuild(clazz);
    }

    // ── Annotation utilities ────────────────────────────────────────

    public static AnnotationProvider toAnnotationProvider(
        final Class<?> element
    ) {
        return new AnnotationProvider() {
            @Override
            public <T extends Annotation> T getAnnotation(
                Class<T> annotationClass
            ) {
                return annotationClass.cast(
                    element.getAnnotation(annotationClass)
                );
            }
        };
    }

    public static AnnotationProvider toAnnotationProvider(
        final Method element
    ) {
        if (element == null) return NULL_ANNOTATION_PROVIDER;
        return new AnnotationProvider() {
            @Override
            public <T extends Annotation> T getAnnotation(
                Class<T> annotationClass
            ) {
                return element.getAnnotation(annotationClass);
            }
        };
    }

    public static Method findMethod(
        Class containingClass,
        String methodName,
        Class... parameterTypes
    ) {
        if (containingClass == null) return null;
        try {
            return containingClass.getMethod(methodName, parameterTypes);
        } catch (SecurityException ex) {
            throw new RuntimeException(ex);
        } catch (NoSuchMethodException ex) {
            return null;
        }
    }

    public static String getServiceId(AnnotatedElement annotated) {
        var sid = annotated.getAnnotation(ServiceId.class);
        if (sid != null) return sid.value();
        var named = annotated.getAnnotation(Named.class);
        if (named != null) {
            var val = named.value();
            if (StringUtils.isNonBlank(val)) return val;
        }
        return null;
    }

    public static <T extends Comparable<T>> List<T> matchAndSort(
        Collection<? extends T> collection,
        Predicate<T> predicate
    ) {
        assert predicate != null;
        return collection
            .stream()
            .filter(predicate)
            .sorted()
            .collect(Collectors.toList());
    }

    public static void validateMarkerAnnotation(Class markerClass) {
        var policy = (Retention) markerClass.getAnnotation(Retention.class);
        if (policy != null && policy.value() == RetentionPolicy.RUNTIME) return;
        throw new IllegalArgumentException(
            UtilMessages.badMarkerAnnotation(markerClass)
        );
    }

    public static void validateMarkerAnnotations(Class[] markerClasses) {
        for (Class c : markerClasses) validateMarkerAnnotation(c);
    }

    // ── I/O utilities ───────────────────────────────────────────────

    public static void close(Closeable stream) {
        if (stream != null) try {
            stream.close();
        } catch (IOException ex) {
            /* ignore */
        }
    }

    private ReflectionUtils() {}
}
