package com.jujin.freeway.ioc;

import com.jujin.freeway.ioc.annotations.EagerLoad;
import com.jujin.freeway.ioc.annotations.Scope;

import java.lang.annotation.Annotation;

/**
 * Allows additional options for a service to be specified, overriding hard
 * coded defaults or defaults from annotations on the service.
 *
 * @see ServiceDefinition
 */
public interface ServiceBindingOptions {
    /**
     * Allows a specific service id for the service to be provided, rather than the
     * default (from the service interface). This is useful when multiple services
     * implement the same interface, since service ids must be unique.
     *
     * @param id
     * @return this binding options, for further configuration
     */
    ServiceBindingOptions withId(String id);

    /**
     * Uses the the simple (unqualified) class name of the implementation class as
     * the id of the service.
     *
     * @return this binding options, for further configuration
     * @throws IllegalStateException
     *             if the class name was not defined (via
     *             {@link ServiceBinder#bind(Class, Class)} or
     *             {@link ServiceBinder#bind(Class)}).
     */
    ServiceBindingOptions withSimpleId();

    /**
     * Sets the scope of the service, overriding the {@link Scope} annotation on the
     * service implementation class.
     *
     * @param scope
     * @return this binding options, for further configuration
     * @see com.jujin.freeway.ioc.internal.util.InternalUtils
     */
    ServiceBindingOptions scope(String scope);

    /**
     * Turns eager loading on for this service. This may also be accomplished using
     * the {@link EagerLoad} annotation on the service implementation class.
     *
     * @return this binding options, for further configuration
     */
    ServiceBindingOptions eagerLoad();

    /**
     * Disallows service decoration for this service.
     *
     * @return this binding options, for further configuration
     */
    ServiceBindingOptions preventDecoration();

    /**
     * Identifies a service for which live class reloading is not desired. This
     * primarily applies to certain internal Freeway services, and is necessary
     * during the development of Freeway itself. In user applications, services
     * defined in library modules are not subject to reloading because the class
     * files are stored in JARs, not as local file system files.
     *
     */
    ServiceBindingOptions preventReloading();

    /**
     * Defines the marker interface(s) for the service, used to connect injections
     * by type at the point of injection with a particular service implementation,
     * based on the intersection of type and marker interface. The containing module
     * will sometimes provide a set of default marker annotations for all services
     * within the module, this method allows that default to be extended.
     *
     * @param marker
     *            one or more markers to add
     * @return this binding options, for further configuration
     */
    @SuppressWarnings("unchecked")
    ServiceBindingOptions withMarker(Class<? extends Annotation>... marker);
}
