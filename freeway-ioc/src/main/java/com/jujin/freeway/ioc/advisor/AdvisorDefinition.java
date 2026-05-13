package com.jujin.freeway.ioc.advisor;

import com.jujin.freeway.ioc.*;

/**
 * Definition of a service advisor, which (by default) is derived from a service
 * advisor method. Service advisor methods are static or instance methods on
 * module classes prefixed with "advise". When a service is realized, a list of
 * matching AdvisorDefs is generated, then ordered, and from each a
 * {@link com.jujin.freeway.ioc.advisor.ServiceAdvisor} is obtained and invoked.
 * <p>
 * Service advisors are applied as an interceptor around the core service
 * implementation, providing method-level advice.
 *
 */
public interface AdvisorDefinition extends Markable {
    /**
     * Returns the id of the advisor, which is derived from the advisor method name.
     */
    String getAdvisorId();

    /**
     * Returns ordering constraints for this advisor, to order it relative to other
     * advisors.
     *
     * @return zero or more constraint strings
     */
    String[] getConstraints();

    /**
     * Creates an object that can provide the service advice (in the default case,
     * by invoking the advise method on the module class or instance).
     *
     * @param moduleSource
     *            used to obtain the module instance
     * @param resources
     *            used to provide injections into the advise method
     * @return advisor
     */
    ServiceAdvisor createAdvisor(
        ModuleBuilderSource moduleSource,
        ServiceResources resources);

    /**
     * Used to determine which services may be advised. When advising a service,
     * first the advisors that target the service are identified, then ordering
     * occurs, then the {@link com.jujin.freeway.ioc.advisor.ServiceAdvisor}s are
     * obtained and invoked.
     *
     * @param serviceDef
     *            identifies a service that may be advised
     * @return true if this advisor applies to the service
     */
    boolean matches(ServiceDefinition serviceDef);
}
