package com.jujin.freeway.ioc.advisor;

import com.jujin.freeway.ioc.AnnotationAccess;

/**
 * A decorator used to create an interceptor that delegates each method's
 * invocation to an {@link com.jujin.freeway.plastic.MethodAdvice} for advice.
 * Advice can inspect or change method parameters, inspect or change the
 * method's return value, and inspect and change thrown exceptions (checked and
 * unchecked).
 */
public interface AspectInterceptor {
    /**
     * Creates a builder that can be used to create the interceptor. This is used
     * when only some of the methods need to be advised, or when different methods
     * need to receive different advice, or when multiple advice is to be applied.
     *
     * @param serviceInterface
     *            defines the interface of the interceptor and the delegate
     * @param delegate
     *            the object on which methods will be invokes
     * @param description
     *            used as the toString() of the interceptor unless toString() is
     *            part of the service interface
     * @param <T>
     *            the type of the service interface.
     * @return a builder that can be used to generate the final interceptor
     */
    <T> AspectInterceptorBuilder<T> createBuilder(Class<T> serviceInterface, T delegate, String description);

    /**
     * Creates a builder that can be used to create the interceptor. This is used
     * when only some of the methods need to be advised, or when different methods
     * need to receive different advice, or when multiple advice is to be applied.
     *
     * @param serviceInterface
     *            defines the interface of the interceptor and the delegate
     * @param delegate
     *            the object on which methods will be invokes
     * @param annotationAccess
     *            provides access to combined annotations of the underlying service
     *            and service interface
     * @param description
     *            used as the toString() of the interceptor unless toString() is
     *            part of the service interface
     * @param <T>
     *            the type of the service interface.
     * @return a builder that can be used to generate the final interceptor
     */
    <T> AspectInterceptorBuilder<T> createBuilder(Class<T> serviceInterface, T delegate,
        AnnotationAccess annotationAccess, String description);
}
