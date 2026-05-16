package com.jujin.freeway.ioc;

/**
 * Injection sources represent an alternate way to locate an object provided
 * somewhere in the {@link com.jujin.freeway.ioc.Registry}. Instead of using a
 * just the service id to gain access to a service within the Registry,
 * injection sources in different flavors are capable of vending, or even
 * creating, objects of disparate types from disparate sources.
 * <p>
 * Injection sources are consulted in a strict order, and the first non-null
 * result is taken.
 * <p>
 * In many cases, an injection source searches for additional annotations on the
 * element (usually a parameter, or perhaps a field) for which a value is
 * required.
 */
public interface InjectionProvider {
    /**
     * Provides an object based on an expression. The process of providing objects
     * occurs within a particular <em>context</em>, which will typically be a
     * service builder method, service contributor method, or service decorator
     * method. The locator parameter provides access to the services visible <em>to
     * that context</em>.
     *
     * @param objectType
     *            the expected object type
     * @param annotationProvider
     *            provides access to annotations (typically, the field or parameter
     *            to which an injection-related annotation is attached); annotations
     *            on the field or parameter may also be used when resolving the
     *            desired object
     * @param locator
     *            locator for the <em>context</em> in which the provider is being
     *            used
     * @param <T>
     * @return the requested object, or null if this injection source can not supply
     *         an object
     * @throws RuntimeException
     *             if the expression can not be evaluated, or the type of object
     *             identified is not assignable to the type specified by the
     *             objectType parameter
     */
    <T> T provide(
        Class<T> objectType,
        AnnotationProvider annotationProvider,
        ServiceLocator locator);
}
