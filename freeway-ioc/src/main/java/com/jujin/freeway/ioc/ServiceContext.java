package com.jujin.freeway.ioc;

import com.jujin.freeway.ioc.advisor.OperationTracker;
import org.slf4j.Logger;

/**
 * Contains resources that may be provided to a service when it initializes,
 * which includes other services defined in the registry. ServiceContext
 * provides access to other services (it extends
 * {@link com.jujin.freeway.ioc.ServiceLocator}).
 */
public interface ServiceContext extends ServiceLocator, AnnotationAccess {
    /**
     * Returns the fully qualified id of the service.
     */
    String getServiceId();

    /**
     * Returns the service interface implemented by the service.
     */
    Class<?> getServiceInterface();

    /**
     * Returns the service implementation.
     */
    Class<?> getServiceImplementation();

    /**
     * Returns a Logger appropriate for logging messages. This includes debug level
     * messages about the creation and configuration of the underlying service, as
     * well as debug, warning, or error level messages from the service itself.
     * Often service interceptors will make use of the service's logger.
     */
    Logger getLogger();

    /**
     * Returns an object that can be used to track operations related to
     * constructing, configuring, decorating and initializing the service.
     */
    OperationTracker getTracker();
}
