package com.jujin.freeway.ioc;

import com.jujin.freeway.ioc.annotations.UsesOrderedConfiguration;

/**
 * A service that acts as a chain-of-command over a number of
 * {@link ServiceProvider}, but allows for the case where
 * no object may be provided.
 * <p>
 * This service is itself a key part of Freeway's general injection mechanism;
 * it is used when instantiating a service implementation instance, invoking
 * module methods (service builder, decorator, or contribution methods), when
 * {@linkplain ServiceLocator#autobuild(Class) autobuilding} objects of any
 * type.
 * <p>
 * As of Freeway 5.3, the ObjectInjector allows injection of
 * {@link com.jujin.freeway.ioc.advisor.OperationTracker} as a special case (not
 * based on a contributed ServiceProvider).
 */
@UsesOrderedConfiguration(ServiceProvider.class)
public interface ObjectInjector {
    /**
     * Injects an object based on an expression. The process of injecting objects
     * occurs within a particular <em>context</em>, which will typically be a
     * service builder method, service contributor method, or service decorator
     * method. The locator parameter provides access to the services visible <em>to
     * that context</em>.
     * <p>
     * When the value is required and no {@link ServiceProvider} provided a non-null
     * value, then {@link ServiceLocator#getService(Class, Class[])} is invoked
     * (with no marker annotations), to provide a uniquely matching service, or
     * throw a failure exception if no <em>single</em> service can be found.
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
     * @param required
     *            if true (normal case) a value must be provided; if false then it
     *            is allowed for no ServiceProvider to provide a value, and this
     *            method may return null to indicate the failure
     * @param <T>
     * @return the requested object, or null if this object provider can not supply
     *         an object
     * @throws RuntimeException
     *             if the expression can not be evaluated, or the type of object
     *             identified is not assignable to the type specified by the
     *             objectType parameter
     */
    <T> T inject(
        Class<T> objectType,
        AnnotationProvider annotationProvider,
        ServiceLocator locator,
        boolean required);
}
