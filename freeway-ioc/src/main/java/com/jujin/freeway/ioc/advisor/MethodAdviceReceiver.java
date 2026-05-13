package com.jujin.freeway.ioc.advisor;

import com.jujin.freeway.ioc.AnnotationAccess;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

/**
 * Interface used with service advisor methods to define advice. Allows advice
 * on specific methods, or on all methods.
 */
public interface MethodAdviceReceiver extends AnnotationAccess {
    /**
     * Advises <em>all</em> methods of the interface with the given advice.
     *
     * @param advice
     *            the method advice to be applied.
     */
    void adviseAllMethods(MethodAdvice advice);

    /**
     * Adds advice for a specific method of the aspect interceptor being
     * constructed.
     *
     * @param method
     *            method (of the interface for which an interceptor is being
     *            constructed) to be advised. Multiple advice is allowed for a
     *            single method; the advice will be executed in the order it is
     *            added.
     * @param advice
     *            the advice for this particular method. Advice must be threadsafe.
     */
    void adviseMethod(Method method, MethodAdvice advice);

    /**
     * Returns the interface for which methods may be advised.
     *
     * @return the interface class instance.
     */
    Class<?> getInterface();

    /**
     * Gets an annotation from a method, via
     * {@link AnnotationAccess#getMethodAnnotationProvider(String, Class...)}.
     *
     * @param <T>
     *            type of annotation
     * @param method
     *            method to search
     * @param annotationType
     *            type of annotation
     * @return the annotation found on the underlying implementation class (if
     *         known) or service interface, or null if not found
     */
    <T extends Annotation> T getMethodAnnotation(
        Method method,
        Class<T> annotationType);
}
