package com.jujin.freeway.ioc;

import com.jujin.freeway.ioc.config.*;
import com.jujin.freeway.ioc.property.*;
import com.jujin.freeway.ioc.threading.*;
import com.jujin.freeway.ioc.classpath.*;
import com.jujin.freeway.ioc.exception.*;
import com.jujin.freeway.ioc.lifecycle.StartupDef;

import com.jujin.freeway.ioc.config.*;
import com.jujin.freeway.ioc.property.*;
import com.jujin.freeway.ioc.threading.*;
import com.jujin.freeway.ioc.classpath.*;
import com.jujin.freeway.ioc.exception.*;
import com.jujin.freeway.ioc.advisor.AdvisorDefinition;

import java.util.Set;
import org.slf4j.Logger;

/**
 * Defines the contents of a module. In the default case, this is information
 * about the services provided by the module builder class.
 */
public interface ModuleDefinition {
    /**
     * Returns the ids of the services built/provided by the module.
     */
    Set<String> getServiceIds();

    /**
     * Returns a service definition via the service's id.
     *
     * @param serviceId
     *            the id of the service to retrieve (case is ignored)
     * @return service definition or null if it doesn't exist
     */
    ServiceDefinition getServiceDef(String serviceId);

    /**
     * Returns all the contribution definitions built/provided by this module.
     */
    Set<ContributionDef> getContributionDefs();

    /**
     * Returns the class that will be instantiated. Annotated instance methods of
     * this class are invoked to build services, to decorate/intercept services, and
     * make contributions to other services.
     * <p>
     * Note: this name is maintained for compatibilty; the term "module builder" is
     * now just "module class".
     */
    Class<?> getBuilderClass();

    /**
     * Returns the name used to create a {@link Logger} instance. This is typically
     * the builder class name.
     */
    String getLoggerName();

    /**
     * Returns all the service advisor definitions built/provided by this module.
     *
     * @return advisor definitions (possibly empty)
     */
    default Set<AdvisorDefinition> getAdvisorDefs() {
        return Set.of();
    }

    /**
     * Methods marked with @Startup are converted into Runnable instances and
     * assigned here.
     *
     * @return startup definitions (possibly empty)
     */
    default Set<StartupDef> getStartups() {
        return Set.of();
    }
}
