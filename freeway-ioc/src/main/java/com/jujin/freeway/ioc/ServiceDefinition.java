package com.jujin.freeway.ioc;

import java.util.Set;

import com.jujin.freeway.ioc.config.*;
import com.jujin.freeway.ioc.property.*;
import com.jujin.freeway.ioc.threading.*;
import com.jujin.freeway.ioc.classpath.*;
import com.jujin.freeway.ioc.exception.*;
import com.jujin.freeway.ioc.lifecycle.ObjectCreator;
import com.jujin.freeway.ioc.config.*;
import com.jujin.freeway.ioc.property.*;
import com.jujin.freeway.ioc.threading.*;
import com.jujin.freeway.ioc.classpath.*;
import com.jujin.freeway.ioc.exception.*;
import com.jujin.freeway.ioc.lifecycle.ServiceLifecycle;

/**
 * Service definition derived, by default, from a service builder method.
 */
public interface ServiceDefinition extends AnnotationAccess {
    /**
     * Returns an {@link ObjectCreator} that can create the core service
     * implementation.
     *
     * @param resources
     *            used to resolve dependencies of the service, or access its
     *            configuration
     * @return an object that can (later) be used to instantiate the service itself
     */
    ObjectCreator<?> createServiceCreator(ServiceBuilderResources resources);

    /**
     * Returns the service id, derived from the method name or the unqualified
     * service interface name. Service ids must be unique among <em>all</em>
     * services in all modules. Service ids are used in a heavy handed way to
     * support ultimate disambiguation, but their primary purpose is to support
     * service contribution methods.
     */
    String getServiceId();

    /**
     * Returns an optional set of <em>marker annotations</em>. Marker annotations
     * are used to disambiguate services; the combination of a marker annotation and
     * a service type is expected to be unique. The annotation is placed on the
     * field or method/constructor parameter and the service is located by combining
     * the marker with service type (the parameter or field type).
     *
     * @return the marker annotations for the service (possibly empty), including
     *         any default marker annotations from the containing module.
     */
    Set<Class<?>> getMarkers();

    /**
     * Returns the service interface associated with this service. This is the
     * interface exposed to the outside world, as well as the one used to build
     * proxies. In cases where the service is <em>not</em> defined in terms of an
     * interface, this will return the actual implementation class of the service.
     * Services without a true service interface are <strong>not proxied</strong>,
     * which has a number of ramifications (such as losing lazy instantiation
     * capabilities and other more interesting lifecycles).
     */
    Class<?> getServiceInterface();

    /**
     * Returns the lifecycle defined for the service. This is indicated by adding a
     * {@link com.jujin.freeway.ioc.annotations.Scope} annotation to the service
     * builder method for the service.
     * <p>
     * Services that are not proxied will ignore their scope; such services are
     * always treated as singletons.
     *
     * @see ServiceLifecycle
     */
    String getServiceScope();

    /**
     * Returns true if the service should be eagerly loaded at Registry startup.
     *
     * @see com.jujin.freeway.ioc.annotations.EagerLoad
     */
    boolean isEagerLoad();

    /**
     * Returns true if the service should not be decorated. Most services allow
     * decoration, unless the
     * {@link com.jujin.freeway.ioc.annotations.PreventServiceDecoration} annotation
     * is present.
     *
     * @return true if decoration should be prevented
     */
    default boolean isPreventDecoration() {
        return false;
    }

    /**
     * Returns the service implementation class, or null.
     *
     * @return the service implementation class, or null
     */
    default Class<?> getServiceImplementation() {
        return null;
    }
}
