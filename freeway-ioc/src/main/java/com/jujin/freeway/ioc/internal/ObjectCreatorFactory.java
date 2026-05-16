package com.jujin.freeway.ioc.internal;

import com.jujin.freeway.ioc.ServiceBuilderContext;
import com.jujin.freeway.ioc.lifecycle.ObjectCreator;

/**
 * An object which can, when passed a {@link ServiceBuilderContext}, create a
 * corresponding {@link ObjectCreator}. A secondary responsibility is to provide
 * a description of the creator, which is usually based on the name of the
 * method or constructor to be invoked, and is ultimately used in some debugging
 * or error output.
 */
public interface ObjectCreatorFactory {
    /**
     * Provides an ObjectCreator that can be used to ultimately instantiate the core
     * service implementation.
     */
    ObjectCreator<?> construct(ServiceBuilderContext resources);

    /**
     * Returns a description of the method or constructor that creates the service.
     */
    String description();
}
