package com.jujin.freeway.ioc.internal;

import com.jujin.freeway.ioc.config.*;
import com.jujin.freeway.ioc.property.*;
import com.jujin.freeway.ioc.threading.*;
import com.jujin.freeway.ioc.classpath.*;
import com.jujin.freeway.ioc.exception.*;
import com.jujin.freeway.ioc.lifecycle.ObjectCreator;
import com.jujin.freeway.ioc.ServiceBuilderResources;

/**
 * An object which can, when passed a {@link ServiceBuilderResources}, create a
 * corresponding {@link ObjectCreator}. A secondary responsibility is to provide
 * a description of the creator, which is usually based on the name of the
 * method or constructor to be invoked, and is ultimately used in some debugging
 * or error output.
 */
public interface ObjectCreatorStrategy {
    /**
     * Provides an ObjectCreator that can be used to ultimately instantiate the core
     * service implementation.
     */
    ObjectCreator<?> constructCreator(ServiceBuilderResources resources);

    /**
     * Returns a description of the method or constructor that creates the service.
     */
    String getDescription();
}
